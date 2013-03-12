/*
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * Copyright (c) 2009 Pentaho Corporation.  All rights reserved.
 */

package org.pentaho.reporting.engine.classic.extensions.datasources.kettle;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.net.URL;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pentaho.reporting.engine.classic.core.metadata.DataFactoryRegistry;
import org.pentaho.reporting.libraries.base.util.FilesystemFilter;
import org.pentaho.reporting.libraries.resourceloader.ResourceManager;

import sun.misc.IOUtils;

/**
 * @author Gretchen Moran
 */
public class TransformationDatasourceMetadata
{

  private static class DirectoryFileFilter implements FileFilter
  {
    private DirectoryFileFilter()
    {
    }

    public boolean accept(final File file)
    {
      if (file.getName().length() > 0 && file.getName().charAt(0) == '.')
      {
        return false;
      }
      return file.isDirectory();
    }
  }

  private static final Log logger = LogFactory.getLog(TransformationDatasourceMetadata.class);
  private static final DirectoryFileFilter DIRECTORY_FILTER = new DirectoryFileFilter();
  private static final FilesystemFilter TEMPLATE_FILES_FILTER =
      new FilesystemFilter(new String[]{".ktr"}, "", false);// NON-NLS

  public static void registerDatasources()
  {

    try
    {
      final ResourceManager resourceManager = new ResourceManager();
      resourceManager.registerDefaults();

      URL templateLocation = TransformationDatasourceMetadata.class.getClassLoader().getResource("datasources");
      if (templateLocation != null){
        final File templateDir = new File(templateLocation.toURI());
        if (templateDir.exists() && templateDir.isDirectory())
        {
          processDirectory(templateDir, resourceManager);
        }
        
      }
    }
    catch (Exception e)
    {
    }
  }

  /**
   * Creates a list of datasources from the templates located in the /datasources directory.
   */
  private static void processDirectory(final File dir,
                                       final ResourceManager resourceManager)
  {
    try
    {
      final File[] dirs = dir.listFiles(DIRECTORY_FILTER);
      if (dirs == null)
      {
        return;
      }
      for (final File f : dirs)
      {
        processDirectory(f, resourceManager);
      }

      final File[] templatesArray = dir.listFiles(TEMPLATE_FILES_FILTER);
      if (templatesArray == null)
      {
        return;
      }

      Arrays.sort(templatesArray);
      for (final File f : templatesArray)
      {
        // TODO: the plugin ID should be preserved even if the template name changes.. not the case today.
        
        byte[] b = IOUtils.readFully(new FileInputStream(f), -1, true);
        DataFactoryRegistry.getInstance().register(
            new EmbeddedKettleDataFactoryMetaData(f.getCanonicalPath(), f.getName().replace(".ktr", ""), 
                f.getAbsolutePath(), b));

        if (logger.isDebugEnabled())
        {
          logger.debug("Datasource metadata successfully registered: ".concat(f.getAbsolutePath()));
        }
      
      }

    }
    catch (Exception se)
    {
      logger.error("Cannot access datasource template directory", se);// NON-NLS
    }
  }

}