package org.solrmarc.marc;

import java.io.*;
import java.lang.reflect.Constructor;
import java.util.*;

import org.apache.log4j.Logger;
import org.marc4j.*;
import org.solrmarc.index.SolrIndexer;
import org.solrmarc.tools.Utils;

public abstract class MarcHandler {

	abstract protected int handleAll();
	abstract protected void loadLocalProperties(Properties props);

	protected SolrIndexer indexer;
	protected MarcReader reader;
	protected boolean verbose = false;
	protected ErrorHandler errors = null;
	protected boolean includeErrors = false;
    /** The full class name of SolrIndexer or the subclass to be used */
	protected String indexerName;
	protected String addnlArgs[] = null;
	
	private String solrmarcPath;
	private String siteSpecificPath;
    /** The name of the _index.properties file */
	private String indexerProps;

	
    // Initialize logging category
    static Logger logger = Logger.getLogger(MarcHandler.class.getName());
	
	public MarcHandler(String args[])
	{
        String configProperties = "import.properties";
        
        if(args.length > 0)
        {
            List<String> addnlArgList = new ArrayList<String>();
            for (String arg : args)
            {
                if (arg.endsWith(".properties"))
                {
                    configProperties = arg;
                }
                else if (arg.endsWith(".mrc"))
                {
                    System.setProperty("marc.path", arg);
                    System.setProperty("marc.source", "FILE");
                }
                else if (arg.endsWith(".del"))
                {
                    System.setProperty("marc.ids_to_delete", arg);
                }
                else
                {
                	addnlArgList.add(arg);
                }
            }
            addnlArgs = addnlArgList.toArray(new String[0]);
        }
 
        // System.out.println("Loading properties from " + properties);
        logger.info("Loading config properties from " + configProperties);
        // Process Properties
        loadProperties(configProperties);

        //  Load the custom Indexer (or the standard one)
        //  note the values indexerName and indexerProps are initialized
        //  by the above call to loadProperties
        loadIndexer(indexerName, indexerProps);
        
	}
		
	/**
	 * Load the properties file and initials class variables
	 * @param configProperties _config.properties file
	 */
	public void loadProperties(String configProperties)
	{
        Properties props = Utils.loadProperties(null, configProperties);

        solrmarcPath = Utils.getProperty(props, "solrmarc.path");

        siteSpecificPath = Utils.getProperty(props, "solrmarc.site.path");

        // class name of SolrIndexer or the subclass to be used
        indexerName = Utils.getProperty(props, "solr.indexer");

        // _index.properties file
        indexerProps = Utils.getProperty(props, "solr.indexer.properties");


        boolean permissiveReader = Boolean.parseBoolean(Utils.getProperty(props, "marc.permissive"));
        String defaultEncoding;
        if (Utils.getProperty(props, "marc.default_encoding") != null)
        {
            defaultEncoding = Utils.getProperty(props, "marc.default_encoding").trim();    
        }
        else
        {
            defaultEncoding = "BESTGUESS";
        }
        verbose = Boolean.parseBoolean(Utils.getProperty(props, "marc.verbose"));
        includeErrors = Boolean.parseBoolean(Utils.getProperty(props, "marc.include_errors"));
        boolean to_utf_8 = Boolean.parseBoolean(Utils.getProperty(props, "marc.to_utf_8"));
        boolean unicodeNormalize = Boolean.parseBoolean(Utils.getProperty(props, "marc.unicode_normalize"));
        String source = Utils.getProperty(props, "marc.source", "STDIN").trim();
        if (Utils.getProperty(props, "marc.override")!= null)
        {
            System.setProperty("org.marc4j.marc.MarcFactory", Utils.getProperty(props, "marc.override").trim());
        }
        reader = null;
        if (source.equals("FILE") || source.equals("STDIN"))
        {
        	InputStream is = null;
        	if (source.equals("FILE")) 
        	{
        		String fName = Utils.getProperty(props, "marc.path").trim();
        		try {
					is = new FileInputStream(fName);
				} 
        		catch (FileNotFoundException e) 
        		{
		        	logger.error("Fatal error: Unable to open specified MARC data file: " + fName);
		        	throw new IllegalArgumentException("Fatal error: Unable to open specified MARC data file: " + fName);
				}
        	}
        	else
        	{
        		is = System.in;
        	}
            if (permissiveReader)
            {
                errors = new ErrorHandler();
                reader = new MarcPermissiveStreamReader(is, errors, to_utf_8, defaultEncoding);
            }
            else
            {
                reader = new MarcPermissiveStreamReader(is, false, to_utf_8, defaultEncoding);
            }
        }
        else if (source.equals("DIR"))
        {
            reader = new MarcDirStreamReader(Utils.getProperty(props, "marc.path").trim(), permissiveReader, to_utf_8);
        }
        else if (source.equals("Z3950"))
        {
        	logger.warn("Error: Z3950 not yet implemented");
            reader = null;
        }
        String marcIncludeIfPresent = Utils.getProperty(props, "marc.include_if_present");
        String marcIncludeIfMissing = Utils.getProperty(props, "marc.include_if_missing");
        if (reader != null && (marcIncludeIfPresent != null || marcIncludeIfMissing != null))
        {
            reader = new MarcFilteredReader(reader, marcIncludeIfPresent, marcIncludeIfMissing);
        }
	//        // Do translating last so that if we are Filtering as well as translating, we don't expend the 
	//        // effort to translate records, which may then be filtered out and discarded.
	//        if (reader != null && to_utf_8)
	//        {
	//            reader = new MarcTranslatedReader(reader, unicodeNormalize);
	//        }
	    loadLocalProperties(props);
	    
        return;
	}

	/**
	 * Load the Custom Indexer routine
	 * @param properties
	 * @throws IOException
	 */
	public void loadIndexer(String indexerName, String indexerProps) 
	{
	    // Setup the SolrMarc Indexer
	    try
	    {
	        Class<?> indexerClass;
	
	        try {
	            indexerClass = Class.forName(indexerName);
	        }
	        catch (ClassNotFoundException e)
	        {
	            logger.error("Cannot load class: " + indexerName);
	            Class<?> baseIndexerClass = SolrIndexer.class;
	            String baseName = baseIndexerClass.getPackage().getName();
	            String fullName = baseName + "." + indexerName;
	            indexerClass = Class.forName(fullName);
	            logger.error(e.getCause());
	        }
	
	        Constructor<?> constructor = indexerClass.getConstructor(new Class[]{String.class, String.class, String.class});
	        Object instance = constructor.newInstance(indexerProps, solrmarcPath, siteSpecificPath);
	
	        if (instance instanceof SolrIndexer)
	        {
	            indexer = (SolrIndexer)instance;
	        }
	        else
	        {
	            logger.error("Error: Custom Indexer " + indexerName + " must be subclass of SolrIndexer. ");
	            throw new IllegalArgumentException("Error: Custom Indexer " + indexerName + " must be subclass of SolrIndexer. ");
	        }
	    }
	    catch (Exception e)
	    {
	        e.printStackTrace();
	
	        if (e instanceof IllegalArgumentException)
	        {
	            logger.error("Error configuring Indexer from properties file.  Exiting...");
	            throw ((IllegalArgumentException) e);
	        }            
	
	        logger.error("Unable to load Custom indexer: " + indexerName);
	        throw new IllegalArgumentException("Error configuring Indexer from properties file.  Exiting...");
	    }
	}
}