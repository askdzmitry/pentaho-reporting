package org.pentaho.reporting.engine.classic.core.layout.process.util;

import org.pentaho.reporting.engine.classic.core.layout.model.FinishedRenderNode;
import org.pentaho.reporting.engine.classic.core.layout.model.RenderBox;
import org.pentaho.reporting.engine.classic.core.layout.model.RenderNode;
import org.pentaho.reporting.engine.classic.core.util.RingBuffer;
import org.pentaho.reporting.libraries.base.util.DebugLog;

public class BlockWidowOrphanContext implements WidowOrphanContext
{
  private StackedObjectPool<BlockWidowOrphanContext> pool;
  private WidowOrphanContext parent;
  private RenderBox contextBox;
  private int widows;
  private int orphans;
  private int count;
  private RingBuffer<Long> orphanSize;
  private RingBuffer<RenderNode> widowSize;
  private boolean debug;
  private long orphanOverride;
  private long widowOverride;

  private RenderNode currentNode;
  private boolean markWidowBoxes;

  public BlockWidowOrphanContext()
  {
  }

  public void init(final StackedObjectPool<BlockWidowOrphanContext> pool,
                   final WidowOrphanContext parent,
                   final RenderBox contextBox,
                   final int widows,
                   final int orphans)
  {
    this.pool = pool;
    this.parent = parent;
    this.contextBox = contextBox;
    this.widows = widows;
    this.orphans = orphans;
    this.widowOverride = contextBox.getY() + contextBox.getHeight();
    this.orphanOverride = contextBox.getY();
    this.markWidowBoxes = contextBox.isOpen() || contextBox.getContentRefCount() > 0;
    this.count = 0;

    if (widows > 0)
    {
      if (this.widowSize == null)
      {
        this.widowSize = new RingBuffer<RenderNode>(widows);
      }
      else
      {
        this.widowSize.resize(widows);
      }
    }
    if (orphans > 0)
    {
      if (this.orphanSize == null)
      {
        this.orphanSize = new RingBuffer<Long>(orphans);
      }
      else
      {
        this.orphanSize.resize(orphans);
      }
    }
  }


  public void startChild(final RenderBox box)
  {
    currentNode = box;

    if (parent != null)
    {
      parent.startChild(box);
    }
  }

  public void endChild(final RenderBox box)
  {
    if (currentNode != null)
    {
      if (count < orphans && orphans > 0)
      {
        final long y2 = box.getY() + box.getHeight();
        orphanSize.add(y2);
        if (debug)
        {
          DebugLog.log("Orphan size added (DIRECT): " + y2 + " -> " + box);
        }
        count += 1;
        box.setRestrictFinishedClearout(RenderBox.RestrictFinishClearOut.LEAF);
      }

      if (widows > 0)
      {
        widowSize.add(box);
        if (debug)
        {
          DebugLog.log("Widow size added (DIRECT): " + box.getY() + " -> " + box);
        }
      }

      currentNode = null;
    }

    if (parent != null)
    {
      parent.endChild(box);
    }
  }

  public void registerFinishedNode(final FinishedRenderNode box)
  {
    if (count < orphans && orphans > 0)
    {
      final long y2 = box.getY() + box.getHeight();
      orphanSize.add(y2);
      if (debug)
      {
        DebugLog.log("Orphan size added (DIRECT): " + y2 + " -> " + box);
      }
      box.getParent().setRestrictFinishedClearout(RenderBox.RestrictFinishClearOut.RESTRICTED);
      count += 1;
    }

    if (widows > 0)
    {
      widowSize.add(box);
      if (debug)
      {
        DebugLog.log("Widow size added (DIRECT): " + box.getY() + " -> " + box);
      }
    }

    currentNode = null;
    if (parent != null)
    {
      parent.registerFinishedNode(box);
    }
  }

  public long getOrphanValue()
  {
    if (orphans == 0)
    {
      return orphanOverride;
    }
    final Long lastValue = orphanSize.getLastValue();
    if (lastValue == null)
    {
      return orphanOverride;
    }
    return Math.max(orphanOverride, lastValue.longValue());
  }

  public long getWidowValue()
  {
    if (widows == 0)
    {
      return widowOverride;
    }
    final RenderNode firstValue = widowSize.getFirstValue();
    if (firstValue == null)
    {
      return widowOverride;
    }
    return Math.min(widowOverride, firstValue.getY());
  }

  public WidowOrphanContext commit(final RenderBox box)
  {
    box.setOrphanConstraintSize(Math.max(0, getOrphanValue() - box.getY()));
    box.setWidowConstraintSize((box.getY() + box.getHeight()) - getWidowValue());

    final boolean incomplete = box.isOpen() || box.getContentRefCount() > 0;
    if (incomplete && count < orphans)
    {
      // the box is either open or has an open sub-report and the orphan constraint is not fulfilled.
      box.setInvalidWidowOrphanNode(true);
    }
    else if (box.getStaticBoxLayoutProperties().isAvoidPagebreakInside())
    {
      if (incomplete)
      {
        box.setInvalidWidowOrphanNode(true);
      }
      else
      {
        box.setInvalidWidowOrphanNode(false);
      }
    }
    else
    {
      // the box is safe to process
      box.setInvalidWidowOrphanNode(false);
    }

    if (widows > 0)
    {
      for (int i = 0; i < widowSize.size(); i += 1)
      {
        final RenderNode widowBox = widowSize.get(i);
        if (widowBox != null)
        {
          widowBox.setWidowBox(markWidowBoxes || widowBox.isWidowBox());
        }
      }
    }

    if (debug)
    {
      DebugLog.log("Final Orphan Size: " + box.getOrphanConstraintSize());
      DebugLog.log("Final Widow Size: " + box.getWidowConstraintSize());
    }
    if (parent != null)
    {
      parent.subContextCommitted(box);
    }

    return parent;
  }

  public void subContextCommitted(final RenderBox contextBox)
  {
    // if there is overlap between the child context and the current lock-out area, process it.
    if (contextBox.getY() <= getOrphanValue())
    {
      orphanOverride = Math.max(orphanOverride, contextBox.getY() + contextBox.getOrphanConstraintSize());
    }

    final long widowLimit = getWidowValue();
    final long contextY2 = contextBox.getY() + contextBox.getHeight();
    if (contextY2 >= widowLimit)
    {
      final long absConstraint = contextY2 - contextBox.getWidowConstraintSize();
      widowOverride = Math.min(widowOverride, absConstraint);
    }

    if (parent != null)
    {
      parent.subContextCommitted(contextBox);
    }
  }

  public void clearForPooledReuse()
  {
    parent = null;
    contextBox = null;
    pool.free(this);
  }
}