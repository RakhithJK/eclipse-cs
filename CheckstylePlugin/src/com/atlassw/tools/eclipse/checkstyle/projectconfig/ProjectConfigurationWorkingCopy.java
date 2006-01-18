//============================================================================
//
// Copyright (C) 2002-2005  David Schneider, Lars K�dderitzsch
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//============================================================================

package com.atlassw.tools.eclipse.checkstyle.projectconfig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.xml.transform.sax.TransformerHandler;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.osgi.util.NLS;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import com.atlassw.tools.eclipse.checkstyle.ErrorMessages;
import com.atlassw.tools.eclipse.checkstyle.config.CheckConfigurationFactory;
import com.atlassw.tools.eclipse.checkstyle.config.CheckConfigurationWorkingCopy;
import com.atlassw.tools.eclipse.checkstyle.config.ICheckConfiguration;
import com.atlassw.tools.eclipse.checkstyle.config.ICheckConfigurationWorkingSet;
import com.atlassw.tools.eclipse.checkstyle.config.configtypes.BuiltInConfigurationType;
import com.atlassw.tools.eclipse.checkstyle.projectconfig.filters.IFilter;
import com.atlassw.tools.eclipse.checkstyle.util.CheckstylePluginException;
import com.atlassw.tools.eclipse.checkstyle.util.XMLUtil;

/**
 * A modifiable project configuration implementation.
 * 
 * @author Lars K�dderitzsch
 */
public class ProjectConfigurationWorkingCopy implements Cloneable, IProjectConfiguration
{

    //
    // attributes
    //

    /** The original, unmodified project configuration. */
    private IProjectConfiguration mProjectConfig;

    /** The local check configurations. */
    private ICheckConfigurationWorkingSet mLocalConfigWorkingSet;

    /** The global check configurations. */
    private ICheckConfigurationWorkingSet mGlobalConfigWorkingSet;

    /** the file sets. */
    private List mFileSets = new LinkedList();

    /** the filters. */
    private List mFilters = new LinkedList();

    /** Flags if the simple file set editor should be used. */
    private boolean mUseSimpleConfig;

    //
    // constructors
    //

    /**
     * Creates a working copy of a given project configuration.
     * 
     * @param projectConfig the project configuration
     */
    public ProjectConfigurationWorkingCopy(IProjectConfiguration projectConfig)
    {

        mProjectConfig = projectConfig;

        mLocalConfigWorkingSet = new LocalCheckConfigurationWorkingSet(this, projectConfig
                .getLocalCheckConfigurations());
        mGlobalConfigWorkingSet = CheckConfigurationFactory.newWorkingSet();

        // clone file sets of the original config
        Iterator it = projectConfig.getFileSets().iterator();
        while (it.hasNext())
        {
            mFileSets.add(((FileSet) it.next()).clone());
        }

        // build list of filters
        List standardFilters = Arrays.asList(PluginFilters.getConfiguredFilters());
        mFilters = new ArrayList(standardFilters);

        // merge with filters configured for the project
        List configuredFilters = projectConfig.getFilters();
        for (int i = 0, size = mFilters.size(); i < size; i++)
        {

            IFilter standardFilter = (IFilter) mFilters.get(i);

            for (int j = 0, size2 = configuredFilters.size(); j < size2; j++)
            {
                IFilter configuredFilter = (IFilter) configuredFilters.get(j);

                if (standardFilter.getInternalName().equals(configuredFilter.getInternalName()))
                {
                    mFilters.set(i, configuredFilter);
                }
            }
        }

        mUseSimpleConfig = projectConfig.isUseSimpleConfig();
    }

    //
    // methods
    //

    /**
     * Returns the check configuration working set for local configurations.
     * 
     * @return the local configurations working set
     */
    public ICheckConfigurationWorkingSet getLocalCheckConfigWorkingSet()
    {
        return mLocalConfigWorkingSet;
    }

    /**
     * Returns the check configuration working set for global configurations.
     * 
     * @return the local configurations working set
     */
    public ICheckConfigurationWorkingSet getGlobalCheckConfigWorkingSet()
    {
        return mGlobalConfigWorkingSet;
    }

    /**
     * Returns a project local check configuration by its name.
     * 
     * @param name the configurations name
     * @return the check configuration or <code>null</code>, if no local
     *         configuration with this name exists
     */
    public ICheckConfiguration getLocalCheckConfigByName(String name)
    {
        ICheckConfiguration config = null;
        ICheckConfiguration[] configs = mLocalConfigWorkingSet.getWorkingCopies();
        for (int i = 0; i < configs.length; i++)
        {
            if (configs[i].getName().equals(name))
            {
                config = configs[i];
                break;
            }
        }

        return config;
    }

    /**
     * Returns a project local check configuration by its name.
     * 
     * @param name the configurations name
     * @return the check configuration or <code>null</code>, if no local
     *         configuration with this name exists
     */
    public ICheckConfiguration getGlobalCheckConfigByName(String name)
    {
        ICheckConfiguration config = null;
        ICheckConfiguration[] configs = mGlobalConfigWorkingSet.getWorkingCopies();
        for (int i = 0; i < configs.length; i++)
        {
            if (configs[i].getName().equals(name))
            {
                config = configs[i];
                break;
            }
        }

        return config;
    }

    /**
     * Sets if the simple configuration should be used.
     * 
     * @param useSimpleConfig true if the project uses the simple fileset
     *            configuration
     */
    public void setUseSimpleConfig(boolean useSimpleConfig)
    {
        mUseSimpleConfig = useSimpleConfig;
    }

    /**
     * Determines if the project configuration changed.
     * 
     * @return <code>true</code> if changed
     */
    public boolean isDirty()
    {
        return !this.equals(mProjectConfig) || mLocalConfigWorkingSet.isDirty();
    }

    /**
     * Stores the project configuration.
     * 
     * @throws CheckstylePluginException error while storing the project
     *             configuration
     */
    public void store() throws CheckstylePluginException
    {
        storeToPersistence(this);
    }

    //
    // implementation of IProjectConfiguration interface
    //

    /**
     * {@inheritDoc}
     */
    public IProject getProject()
    {
        return mProjectConfig.getProject();
    }

    /**
     * {@inheritDoc}
     */
    public List getLocalCheckConfigurations()
    {
        return Arrays.asList(mLocalConfigWorkingSet.getWorkingCopies());
    }

    /**
     * {@inheritDoc}
     */
    public List getFileSets()
    {
        return mFileSets;
    }

    /**
     * {@inheritDoc}
     */
    public List getFilters()
    {
        return mFilters;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isUseSimpleConfig()
    {
        return mUseSimpleConfig;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isConfigInUse(ICheckConfiguration configuration)
    {

        boolean result = false;

        Iterator iter = getFileSets().iterator();
        while (iter.hasNext())
        {
            FileSet fileSet = (FileSet) iter.next();
            ICheckConfiguration checkConfig = fileSet.getCheckConfig();
            if (configuration.equals(checkConfig)
                    || (checkConfig instanceof CheckConfigurationWorkingCopy && configuration
                            .equals(((CheckConfigurationWorkingCopy) checkConfig)
                                    .getSourceCheckConfiguration())))
            {
                result = true;
                break;
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public Object clone()
    {
        ProjectConfigurationWorkingCopy clone = null;
        try
        {
            clone = (ProjectConfigurationWorkingCopy) super.clone();
            clone.mFileSets = new LinkedList();
            clone.setUseSimpleConfig(this.isUseSimpleConfig());

            // clone file sets
            Iterator iter = getFileSets().iterator();
            while (iter.hasNext())
            {
                clone.getFileSets().add(((FileSet) iter.next()).clone());
            }

            // clone filters
            List clonedFilters = new ArrayList();
            iter = getFilters().iterator();
            while (iter.hasNext())
            {
                clonedFilters.add(((IFilter) iter.next()).clone());
            }
            clone.mFilters = clonedFilters;
        }
        catch (CloneNotSupportedException e)
        {
            throw new InternalError();
        }

        return clone;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj)
    {

        if (obj == null || !(obj instanceof IProjectConfiguration))
        {
            return false;
        }
        if (this == obj)
        {
            return true;
        }
        IProjectConfiguration rhs = (IProjectConfiguration) obj;
        return new EqualsBuilder().append(getProject(), rhs.getProject()).append(
                isUseSimpleConfig(), rhs.isUseSimpleConfig()).append(getFileSets(),
                rhs.getFileSets()).append(getFilters(), rhs.getFilters()).isEquals();
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode()
    {
        return new HashCodeBuilder(984759323, 1000003).append(mProjectConfig).append(
                mUseSimpleConfig).append(mFileSets).append(mFilters).toHashCode();
    }

    /**
     * Store the audit configurations to the persistent state storage.
     */
    private static void storeToPersistence(ProjectConfigurationWorkingCopy config)
        throws CheckstylePluginException
    {

        ByteArrayOutputStream pipeOut = null;
        InputStream pipeIn = null;
        try
        {

            pipeOut = new ByteArrayOutputStream();

            // Write the configuration document by pushing sax events through
            // the transformer handler
            TransformerHandler xmlOut = XMLUtil.writeWithSax(pipeOut);

            writeProjectConfig(config, xmlOut);

            pipeIn = new ByteArrayInputStream(pipeOut.toByteArray());

            // create or overwrite the .checkstyle file
            IProject project = config.getProject();
            IFile file = project.getFile(ProjectConfigurationFactory.PROJECT_CONFIGURATION_FILE);
            if (!file.exists())
            {
                file.create(pipeIn, true, null);
                file.setLocal(true, IResource.DEPTH_INFINITE, null);
            }
            else
            {
                file.setContents(pipeIn, true, true, null);
            }

            config.getLocalCheckConfigWorkingSet().store();
        }
        catch (Exception e)
        {
            CheckstylePluginException.rethrow(e, NLS.bind(
                    ErrorMessages.errorWritingCheckConfigurations, e.getLocalizedMessage()));
        }
        finally
        {
            try
            {
                pipeOut.close();
            }
            catch (Exception e1)
            {
                // can nothing do about it
            }
            try
            {
                pipeIn.close();
            }
            catch (Exception e1)
            {
                // can nothing do about it
            }
        }
    }

    /**
     * Produces the sax events to write a project configuration.
     * 
     * @param config the configuration
     * @param xmlOut the transformer handler receiving the events
     * @throws SAXException error writing
     */
    private static void writeProjectConfig(ProjectConfigurationWorkingCopy config,
            TransformerHandler xmlOut) throws SAXException, CheckstylePluginException
    {

        xmlOut.startDocument();

        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute(new String(), XMLTags.FORMAT_VERSION_TAG, XMLTags.FORMAT_VERSION_TAG,
                null, ProjectConfigurationFactory.CURRENT_FILE_FORMAT_VERSION);
        attr.addAttribute(new String(), XMLTags.SIMPLE_CONFIG_TAG, XMLTags.SIMPLE_CONFIG_TAG, null,
                new String() + config.isUseSimpleConfig());

        xmlOut.startElement(new String(), XMLTags.FILESET_CONFIG_TAG, XMLTags.FILESET_CONFIG_TAG,
                attr);
        xmlOut.ignorableWhitespace(new char[] { '\n' }, 0, 1);

        ICheckConfiguration[] workingCopies = config.getLocalCheckConfigWorkingSet()
                .getWorkingCopies();
        for (int i = 0; i < workingCopies.length; i++)
        {
            writeLocalConfiguration(workingCopies[i], xmlOut);
        }

        List fileSets = config.getFileSets();
        int size = fileSets != null ? fileSets.size() : 0;
        for (int i = 0; i < size; i++)
        {
            writeFileSet((FileSet) fileSets.get(i), config.getProject(), xmlOut);
        }
        // write filters
        List filters = config.getFilters();
        size = filters != null ? filters.size() : 0;
        for (int i = 0; i < size; i++)
        {
            writeFilter((IFilter) filters.get(i), xmlOut);
        }

        xmlOut.endElement(new String(), XMLTags.FILESET_CONFIG_TAG, XMLTags.FILESET_CONFIG_TAG);
        xmlOut.endDocument();
    }

    /**
     * Writes a local check configuration.
     * 
     * @param checkConfig the local check configuration
     * @param xmlOut the transformer handler receiving the events
     * @throws SAXException error writing
     * @throws CheckstylePluginException
     */
    private static void writeLocalConfiguration(ICheckConfiguration checkConfig,
            TransformerHandler xmlOut) throws SAXException, CheckstylePluginException
    {

        // don't store built-in configurations to persistence or local
        // configurations
        if (checkConfig.getType() instanceof BuiltInConfigurationType || checkConfig.isGlobal())
        {
            return;
        }

        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute(new String(), XMLTags.NAME_TAG, XMLTags.NAME_TAG, null, checkConfig
                .getName());
        attrs.addAttribute(new String(), XMLTags.LOCATION_TAG, XMLTags.LOCATION_TAG, null,
                checkConfig.getLocation());
        attrs.addAttribute(new String(), XMLTags.TYPE_TAG, XMLTags.TYPE_TAG, null, checkConfig
                .getType().getInternalName());
        if (checkConfig.getDescription() != null)
        {
            attrs.addAttribute(new String(), XMLTags.DESCRIPTION_TAG, XMLTags.DESCRIPTION_TAG,
                    null, checkConfig.getDescription());
        }

        xmlOut
                .startElement(new String(), XMLTags.CHECK_CONFIG_TAG, XMLTags.CHECK_CONFIG_TAG,
                        attrs);

        Iterator addDataIterator = checkConfig.getAdditionalData().keySet().iterator();
        while (addDataIterator.hasNext())
        {
            String key = (String) addDataIterator.next();
            String value = (String) checkConfig.getAdditionalData().get(key);

            attrs = new AttributesImpl();
            attrs.addAttribute(new String(), XMLTags.NAME_TAG, XMLTags.NAME_TAG, null, key);
            attrs.addAttribute(new String(), XMLTags.VALUE_TAG, XMLTags.VALUE_TAG, null, value);

            xmlOut.startElement(new String(), XMLTags.ADDITIONAL_DATA_TAG,
                    XMLTags.ADDITIONAL_DATA_TAG, attrs);
            xmlOut.endElement(new String(), XMLTags.ADDITIONAL_DATA_TAG,
                    XMLTags.ADDITIONAL_DATA_TAG);
        }

        xmlOut.endElement(new String(), XMLTags.CHECK_CONFIG_TAG, XMLTags.CHECK_CONFIG_TAG);
        xmlOut.ignorableWhitespace(new char[] { '\n' }, 0, 1);
    }

    /**
     * Produces the sax events to write a file set to xml.
     * 
     * @param fileSet the file set
     * @param project the project
     * @param xmlOut the transformer handler receiving the events
     * @throws SAXException error writing
     */
    private static void writeFileSet(FileSet fileSet, IProject project, TransformerHandler xmlOut)
        throws SAXException, CheckstylePluginException
    {

        if (fileSet.getCheckConfig() == null)
        {
            throw new CheckstylePluginException(ErrorMessages.bind(
                    ErrorMessages.errorFilesetWithoutCheckConfig, fileSet.getName(), project
                            .getName()));
        }

        AttributesImpl attr = new AttributesImpl();
        attr
                .addAttribute(new String(), XMLTags.NAME_TAG, XMLTags.NAME_TAG, null, fileSet
                        .getName());

        attr.addAttribute(new String(), XMLTags.ENABLED_TAG, XMLTags.ENABLED_TAG, null,
                new String() + fileSet.isEnabled());

        ICheckConfiguration checkConfig = fileSet.getCheckConfig();
        if (checkConfig != null)
        {

            attr.addAttribute(new String(), XMLTags.CHECK_CONFIG_NAME_TAG,
                    XMLTags.CHECK_CONFIG_NAME_TAG, null, checkConfig.getName());
            attr.addAttribute(new String(), XMLTags.LOCAL_TAG, XMLTags.LOCAL_TAG, null, ""
                    + !checkConfig.isGlobal());
        }

        xmlOut.startElement(new String(), XMLTags.FILESET_TAG, XMLTags.FILESET_TAG, attr);
        xmlOut.ignorableWhitespace(new char[] { '\n' }, 0, 1);

        // write patterns
        List patterns = fileSet.getFileMatchPatterns();
        int size = patterns != null ? patterns.size() : 0;
        for (int i = 0; i < size; i++)
        {
            writeMatchPattern((FileMatchPattern) patterns.get(i), xmlOut);
        }

        xmlOut.endElement(new String(), XMLTags.FILESET_TAG, XMLTags.FILESET_TAG);
        xmlOut.ignorableWhitespace(new char[] { '\n' }, 0, 1);
    }

    /**
     * Produces the sax events to write the file match pattern to xml.
     * 
     * @param pattern the pattern
     * @param xmlOut the transformer handler receiving the events
     * @throws SAXException error writing
     */
    private static void writeMatchPattern(FileMatchPattern pattern, TransformerHandler xmlOut)
        throws SAXException
    {

        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute(new String(), XMLTags.MATCH_PATTERN_TAG, XMLTags.MATCH_PATTERN_TAG, null,
                pattern.getMatchPattern() != null ? pattern.getMatchPattern() : ""); //$NON-NLS-1$
        attr.addAttribute(new String(), XMLTags.INCLUDE_PATTERN_TAG, XMLTags.INCLUDE_PATTERN_TAG,
                null, new String() + pattern.isIncludePattern());

        xmlOut.startElement(new String(), XMLTags.FILE_MATCH_PATTERN_TAG,
                XMLTags.FILE_MATCH_PATTERN_TAG, attr);
        xmlOut.endElement(new String(), XMLTags.FILE_MATCH_PATTERN_TAG,
                XMLTags.FILE_MATCH_PATTERN_TAG);
        xmlOut.ignorableWhitespace(new char[] { '\n' }, 0, 1);
    }

    /**
     * Produces the sax events to write a filter to xml.
     * 
     * @param filter the filter
     * @param xmlOut the transformer handler receiving the events
     * @throws SAXException error writing
     */
    private static void writeFilter(IFilter filter, TransformerHandler xmlOut) throws SAXException
    {

        // write only filters that are actually changed
        // (enabled or contain data)
        IFilter prototype = PluginFilters.getByInternalName(filter.getInternalName());
        if (prototype.equals(filter))
        {
            return;
        }

        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute(new String(), XMLTags.NAME_TAG, XMLTags.NAME_TAG, null, filter
                .getInternalName());
        attr.addAttribute(new String(), XMLTags.ENABLED_TAG, XMLTags.ENABLED_TAG, null,
                new String() + filter.isEnabled());

        xmlOut.startElement(new String(), XMLTags.FILTER_TAG, XMLTags.FILTER_TAG, attr);
        xmlOut.ignorableWhitespace(new char[] { '\n' }, 0, 1);

        List data = filter.getFilterData();
        int size = data != null ? data.size() : 0;
        for (int i = 0; i < size; i++)
        {

            attr = new AttributesImpl();
            attr.addAttribute(new String(), XMLTags.VALUE_TAG, XMLTags.VALUE_TAG, null,
                    (String) data.get(i));

            xmlOut.startElement(new String(), XMLTags.FILTER_DATA_TAG, XMLTags.FILTER_DATA_TAG,
                    attr);
            xmlOut.endElement(new String(), XMLTags.FILTER_DATA_TAG, XMLTags.FILTER_DATA_TAG);
            xmlOut.ignorableWhitespace(new char[] { '\n' }, 0, 1);
        }

        xmlOut.endElement(new String(), XMLTags.FILTER_TAG, XMLTags.FILTER_TAG);
        xmlOut.ignorableWhitespace(new char[] { '\n' }, 0, 1);

    }
}