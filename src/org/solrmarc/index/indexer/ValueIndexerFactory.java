package org.solrmarc.index.indexer;

import org.apache.log4j.Logger;
import org.solrmarc.index.collector.MultiValueCollector;
import org.solrmarc.index.extractor.AbstractMultiValueExtractor;
import org.solrmarc.index.extractor.AbstractSingleValueExtractor;
import org.solrmarc.index.extractor.AbstractValueExtractor;
import org.solrmarc.index.extractor.AbstractValueExtractorFactory;
import org.solrmarc.index.extractor.formatter.FieldFormatterMapped;
import org.solrmarc.index.extractor.formatter.FieldFormatter.eCleanVal;
import org.solrmarc.index.extractor.formatter.FieldFormatter.eJoinVal;
import org.solrmarc.index.extractor.impl.direct.DirectMultiValueExtractor;
import org.solrmarc.index.extractor.methodcall.StaticMarcTestRecords;
import org.solrmarc.index.mapping.AbstractMultiValueMapping;
import org.solrmarc.index.mapping.AbstractValueMappingFactory;
import org.solrmarc.index.utils.FastClasspathUtils;
//import org.solrmarc.index.utils.ReflectionUtils;
import org.solrmarc.tools.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValueIndexerFactory
{
    private final static Logger logger = Logger.getLogger(ValueIndexerFactory.class);
    private final List<AbstractValueExtractorFactory> extractorFactories;
    private final List<AbstractValueMappingFactory> mappingFactories;
    private List<IndexerSpecException> validationExceptions;
    private List<IndexerSpecException> perRecordExceptions;
    private FullConditionalParser parser = null;    
    private Properties localMappingProperties = null;
    private static String homeDir = ".";
    private final Pattern specPattern = Pattern.compile("([-A-Za-z_0-9, \\t]*)([:=]|([+]=))(.*)");
    boolean debug_parse = false;
    private boolean defaultUniqueVal = true;
    private final Pattern defaultUniquePattern = Pattern.compile("default.unique[ ]*[;=][ ]*[\"]?(true|false)[\"]?");

    /**
     *  The next three functions make the ValueIndexerFactory implement the Singleton pattern
     *  To create of use a Factory  do:
     *  ValueIndexerFactory.instance()
     */
    private static ValueIndexerFactory theFactory = null;
    
    public static ValueIndexerFactory instance()
    {
        if (theFactory == null)
            theFactory = new ValueIndexerFactory();
        return(theFactory);
    }
    
    private ValueIndexerFactory()
    {
        validationExceptions = new ArrayList<IndexerSpecException>();
        perRecordExceptions = null;
        try
        {
            this.extractorFactories = createExtractorFactories(FastClasspathUtils.getExtractorFactoryClasses());
            this.mappingFactories = createMappingFactories(FastClasspathUtils.getMappingFactoryClasses());
        }
        catch (IllegalAccessException | InstantiationException e)
        {
            throw new IndexerSpecException(e, "Error creating extractor or mapping factories");
        }
    }

    /**
     * Return ALL of the exceptions encountered while processing indexing specification
     * @return
     */
    public List<IndexerSpecException> getValidationExceptions()
    {
        return validationExceptions;
    }
    
    /**
     * Return ALL of the exceptions encountered while processing indexing specification
     * @return
     */
    public void addPerRecordError(IndexerSpecException error)
    {
        if (perRecordExceptions == null)
        {
            perRecordExceptions = new LinkedList<>();
        }
        perRecordExceptions.add(error);
    }
    
    /**
     * Return the mapping factories loaded above for use in the CUP parser
     * @return
     */
    public List<AbstractValueMappingFactory> getMappingFactories()
    {
        return mappingFactories;
    }

    /**
     * Return ALL of the exceptions encountered while processing indexing specification
     * @return
     */
    public List<IndexerSpecException> getPerRecordErrors()
    {
        return perRecordExceptions;
    }

    public void clearPerRecordErrors()
    {
        perRecordExceptions = null;
    }

    public Properties getLocalMappingProperties()
    {
        return localMappingProperties;
    }
        
    public boolean isDebugParse()
    {
        return debug_parse;
    }

    public void setDebugParse(boolean debug_parse)
    {
        this.debug_parse = debug_parse;
    }

    public List<AbstractValueIndexer<?>> createValueIndexers(File indexSpecFiles[])
            throws IllegalAccessException, InstantiationException, IOException
    {
        validationExceptions.clear();
        localMappingProperties = new Properties();

        Map<String, List<AbstractValueIndexer<?>>> valueIndexerMap = new LinkedHashMap<>();

        createValueIndexers(indexSpecFiles, valueIndexerMap);
        
        List<AbstractValueIndexer<?>> valueIndexers = collapseMapToList(valueIndexerMap);
                
        return valueIndexers;
    }
    
    public List<AbstractValueIndexer<?>> createValueIndexers(File indexSpecFile)
            throws IllegalAccessException, InstantiationException, IOException
    {
        validationExceptions.clear();
        localMappingProperties = new Properties();

        Map<String, List<AbstractValueIndexer<?>>> valueIndexerMap = new LinkedHashMap<>();

        createValueIndexers(indexSpecFile, valueIndexerMap);
        
        List<AbstractValueIndexer<?>> valueIndexers = collapseMapToList(valueIndexerMap);
                
        return valueIndexers;
    }
    
    public List<AbstractValueIndexer<?>> createValueIndexers(String configSpecs[])
            throws IllegalAccessException, InstantiationException
    {
        validationExceptions.clear();
        localMappingProperties = new Properties();

        Map<String, List<AbstractValueIndexer<?>>> valueIndexerMap = new LinkedHashMap<>();
        
        createValueIndexers(configSpecs, valueIndexerMap);
      
        List<AbstractValueIndexer<?>> valueIndexers = collapseMapToList(valueIndexerMap);
        
        return valueIndexers;
    }
    
    private List<AbstractValueIndexer<?>> collapseMapToList(Map<String, List<AbstractValueIndexer<?>>> valueIndexerMap)
    {
        List<AbstractValueIndexer<?>> valueIndexers = new ArrayList<>();
        for (List<AbstractValueIndexer<?>> indexer : valueIndexerMap.values())
        {
            valueIndexers.addAll(indexer);
        }
        return valueIndexers;
    }

    private void createValueIndexers(File indexSpecFiles[], Map<String, List<AbstractValueIndexer<?>>> valueIndexerMap)
            throws IllegalAccessException, InstantiationException, IOException
    {
        for (File indexSpecFile : indexSpecFiles)
        {
            createValueIndexers(indexSpecFile, valueIndexerMap);
        }
    }
    
    private void createValueIndexers(File indexSpecFile, Map<String, List<AbstractValueIndexer<?>>> valueIndexerMap)
            throws IllegalAccessException, InstantiationException, IOException
    {
        List<String> lines = new ArrayList<String>();
        BufferedReader reader = new BufferedReader(new FileReader(indexSpecFile));
        String line;
        while ((line = reader.readLine()) != null)
        {
            lines.add(line);
        }
        createValueIndexers(lines.toArray(new String[0]), valueIndexerMap);
        reader.close();
    }
    
    public void createValueIndexers(String configSpecs[], Map<String, List<AbstractValueIndexer<?>>> valueIndexerMap)
            throws IllegalAccessException, InstantiationException
    {
        for (final String singleSpec : configSpecs)
        {
            if (singleSpec.startsWith("map.") || singleSpec.startsWith("pattern_map.")) 
            {
                final String[] specParts = singleSpec.split("[ ]?[:=][ ]?", 2);
                localMappingProperties.put(specParts[0].trim(), specParts[1].trim());
            }
        }
        for (String singleSpec : configSpecs)
        {
            singleSpec = singleSpec.trim();
            if (singleSpec.startsWith("#") || (!singleSpec.contains(":") && !singleSpec.contains("="))) continue;
            if (singleSpec.startsWith("map.") || singleSpec.startsWith("pattern_map.")) continue;
            if (singleSpec.startsWith("default"))
            {
                if (singleSpec.startsWith("default.unique"))
                {
                    Matcher defUnique = defaultUniquePattern.matcher(singleSpec);
                    defUnique.matches();
                    this.defaultUniqueVal = Boolean.parseBoolean(defUnique.group(1));
                }
            }
     //       if (singleSpec.startsWith("marc.") || singleSpec.startsWith("solrmarc.")) continue;
            
            // Pattern.compile("([-A-Za-z_0-9, \\t]*)([:=]|([+]=))(.*)");

            Matcher match = specPattern.matcher(singleSpec);
            if (match.matches())
            {
                final String solrFieldName = match.group(1).replaceAll("[ \\t]", "");
                final String delimiter = match.group(2);
                final String mappingDefinition = match.group(4);
                try
                {
                    MultiValueIndexer valueIndexer = createValueIndexer(solrFieldName, mappingDefinition);
                    if (valueIndexer != null)
                    {
                        List<AbstractValueIndexer<?>> indexerList;
                        if (delimiter.startsWith("+") && valueIndexerMap.containsKey(solrFieldName))
                        {
                            indexerList = valueIndexerMap.get(solrFieldName);
                        }
                        else
                        {
                            indexerList = new ArrayList<>();
                        }
                        indexerList.add(valueIndexer);
                        valueIndexerMap.put(solrFieldName, indexerList);
                    }
                    for (IndexerSpecException ise  : parser.getErrors())
                    {
                        ise.setSolrFieldAndSpec(solrFieldName, mappingDefinition);
                        validationExceptions.add(ise);
                    }
                }
                catch (IndexerSpecException ise)
                {
                    ise.setSolrFieldAndSpec(solrFieldName, mappingDefinition);
                    validationExceptions.add(ise);
                }
            }
        }
    }

    public MultiValueIndexer createValueIndexer(String fieldNames, String indexSpec)
    {
        String fullSpec = fieldNames + " = " + indexSpec;
        if (parser == null) 
        {
            try
            {
                parser = new FullConditionalParser(debug_parse);
                parser.setFactories(this, this.extractorFactories, this.mappingFactories);
            }
            catch (IllegalAccessException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            catch (InstantiationException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        logger.trace("Processing spec: "+ indexSpec);
        MultiValueIndexer valueIndexer = parser.parse(fieldNames, indexSpec);
        // Test fire the indexer to catch obvious error such as missing property files
        if (valueIndexer != null)
        {
            try
            {
                logger.trace("Test firing spec: "+ indexSpec);
                valueIndexer.getFieldData(StaticMarcTestRecords.testRecord[0]);
            }
            catch (InvocationTargetException ite)
            {
                throw new IndexerSpecException(ite.getTargetException(), "Error on test invocation of custom method: " + indexSpec);
            }
            catch (Exception e)
            {
                throw new IndexerSpecException(e, "Error on test invocation of custom method: " + indexSpec);
            }
        }
        return(valueIndexer);
    }
    
    private List<AbstractValueExtractorFactory> createExtractorFactories(
            final Set<Class<? extends AbstractValueExtractorFactory>> factoryClasses)
            throws IllegalAccessException, InstantiationException
    {
        final List<AbstractValueExtractorFactory> factories = new ArrayList<>(factoryClasses.size());
        for (final Class<? extends AbstractValueExtractorFactory> extractorFactoryClass : factoryClasses)
        {
            if (Modifier.isAbstract(extractorFactoryClass.getModifiers()))
            {
                continue;
            }
            logger.trace("Create value extractor factory for " + extractorFactoryClass);
            final AbstractValueExtractorFactory factory = extractorFactoryClass.newInstance();
            factories.add(factory);
        }
        return factories;
    }

    private List<AbstractValueMappingFactory> createMappingFactories(
            final Set<Class<? extends AbstractValueMappingFactory>> factoryClasses)
            throws IllegalAccessException, InstantiationException
    {
        final List<AbstractValueMappingFactory> factories = new ArrayList<>(factoryClasses.size());
        for (final Class<? extends AbstractValueMappingFactory> extractorFactoryClass : factoryClasses)
        {
            logger.trace("Create value mapping factory for  s " + extractorFactoryClass);
            final AbstractValueMappingFactory factory = extractorFactoryClass.newInstance();
            factories.add(factory);
        }
        return factories;
    }
 
    
    /**
     * Creates an indexer representing the indexer process for one (or more) solr field
     * (given by fieldnames), Where the extractor has already been created in the parser handling code, and the
     * maps definitions following the extractor have been pulled out as a list of lists of strings.
     *
     * @param origSpec
     *            the original index specification (for debugging purposes).
     * @param solrFieldName
     *            the name (or names) of the solr field(s) this spec should generate.
     * @param extractor
     *            the extractor object created by the parser handling code (or perhaps one manually created)
     * @param mapSpecs
     *            the mapping/collector specifications definition for this indexer.
     * @return an indexer representing the indexer process for one solr field.
     */
    public AbstractValueIndexer<?> makeMultiValueIndexer(String origSpec, List<String> fieldnames, AbstractValueExtractor<?> extractor, List<List<String>> mapSpecs)
    {
        if (mapSpecs == null)
        {
            mapSpecs = new ArrayList<List<String>>();
        }
        if (extractor instanceof DirectMultiValueExtractor)
        {
            final DirectMultiValueExtractor multiValueExtractor = (DirectMultiValueExtractor) extractor;
            int indexOfJoin = decorateMultiValueExtractor(origSpec, fieldnames, multiValueExtractor, mapSpecs);
            final List<AbstractMultiValueMapping> mappings;
            if (indexOfJoin != -1)
                mappings = createMultiValueMappings(origSpec, mapSpecs, indexOfJoin);
            else 
                mappings = new ArrayList<AbstractMultiValueMapping>();
            final MultiValueCollector collector = createMultiValueCollector(mapSpecs);

            return new MultiValueIndexer(fieldnames, multiValueExtractor, mappings, collector);
        }
        else if (extractor instanceof AbstractMultiValueExtractor)
        {
            final AbstractMultiValueExtractor multiValueExtractor = (AbstractMultiValueExtractor) extractor;
            final List<AbstractMultiValueMapping> mappings = createMultiValueMappings(origSpec, mapSpecs);
            final MultiValueCollector collector = createMultiValueCollector(mapSpecs);
            return new MultiValueIndexer(fieldnames, multiValueExtractor, mappings, collector);
        }
        else if (extractor instanceof AbstractSingleValueExtractor)
        {
            final AbstractSingleValueExtractor singleValueExtractor = (AbstractSingleValueExtractor) extractor;
            final List<AbstractMultiValueMapping> mappings = createMultiValueMappings(origSpec, mapSpecs);
            final MultiValueCollector collector = createMultiValueCollector(mapSpecs);
            return new MultiValueIndexer(fieldnames, singleValueExtractor, mappings, collector);
        }
        else
        {
            throw new IllegalArgumentException(
                    "Only subclasses of AbstractMultiValueExtractor or AbstractSingleValueExtractor are allowed, but not "
                            + extractor.getClass().getName());
        } 
    }

    
    boolean isADecoratorConfiguration(String str)
    {
        if (str.equals("join") || str.equals("separate") || str.equals("format") || str.equals("substring") || 
            str.equals("cleanEach") || str.equals("cleanEnd") || str.equals("clean") || str.equals("stripAccent") || 
            str.equals("stripPunct") || str.equals("stripInd2") || str.equals("toUpper") || str.equals("toLower") || 
            str.equals("toUpper") || str.equals("toLower") || str.equals("titleSortUpper") || str.equals("titleSortLower"))
            return(true);
        return(false);
    }
      
    private int decorateMultiValueExtractor(String origSpec, List<String> fieldnames, DirectMultiValueExtractor multiValueExtractor, List<List<String>> mapSpecs)
    {
        if (mapSpecs.size() == 0)
        {
            return -1;
        }
        
        int currentIndex = 0;
        int joinIndex = -1;
        for (List<String> mapSpec : mapSpecs)
        {
            String mapParts[] = mapSpec.toArray(new String[0]);
            if (isACollectorConfiguration(mapParts[0]))
            {
                /* ignore, handle it elsewhere */
            }
            else if (mapParts[0].equals("join"))
            {
                multiValueExtractor.setJoinVal(eJoinVal.JOIN);
                if (mapParts.length > 1)
                {
                    multiValueExtractor.setSeparator(mapParts[1]);
                }
                joinIndex = currentIndex;
            }
            else if (mapParts[0].equals("separate"))
            {
                multiValueExtractor.setJoinVal(eJoinVal.SEPARATE);
            }
            else if (mapParts[0].equals("format"))
            {
                if (mapParts.length > 1)
                {
                    multiValueExtractor.setFormatPatterns(mapParts);
                }
            }
            else if (mapParts[0].equals("substring"))
            {
                try {
                    if (mapParts.length > 2)
                    {
                        multiValueExtractor.setSubstring(mapParts[1], mapParts[2]);
                    }
                    else
                    {
                        multiValueExtractor.setSubstring(mapParts[1], mapParts[1]);
                    }
                }
                catch (IndexerSpecException ise)
                {
                    ise.setSolrFieldAndSpec(origSpec);
                    validationExceptions.add(ise);
                }
            }
            else if (mapParts[0].equals("cleanEach"))
            {
                multiValueExtractor.addCleanVal(eCleanVal.CLEAN_EACH);
            }
            else if (mapParts[0].equals("cleanEnd"))
            {
                multiValueExtractor.addCleanVal(eCleanVal.CLEAN_END);
            }
            else if (mapParts[0].equals("clean"))
            {
                multiValueExtractor.addCleanVal(eCleanVal.CLEAN_EACH);
                multiValueExtractor.addCleanVal(eCleanVal.CLEAN_END);
            }
            else if (mapParts[0].equals("stripAccent"))
            {
                multiValueExtractor.addCleanVal(eCleanVal.STRIP_ACCCENTS);
            }
            else if (mapParts[0].equals("stripPunct"))
            {
                multiValueExtractor.addCleanVal(eCleanVal.STRIP_ALL_PUNCT);
            }
            else if (mapParts[0].equals("stripInd2"))
            {
                multiValueExtractor.addCleanVal(eCleanVal.STRIP_INDICATOR_2);
            }
            else if (mapParts[0].equals("toUpper"))
            {
                multiValueExtractor.addCleanVal(eCleanVal.TO_UPPER);
            }
            else if (mapParts[0].equals("toLower"))
            {
                multiValueExtractor.addCleanVal(eCleanVal.TO_LOWER);
            }
            else if (mapParts[0].equals("toUpper"))
            {
                multiValueExtractor.addCleanVal(eCleanVal.TO_UPPER);
            }
            else if (mapParts[0].equals("toLower"))
            {
                multiValueExtractor.addCleanVal(eCleanVal.TO_LOWER);
            }
            else if (mapParts[0].equals("titleSortUpper"))
            {
                multiValueExtractor.setCleanVal(EnumSet.of(eCleanVal.CLEAN_EACH, eCleanVal.CLEAN_END, eCleanVal.STRIP_ACCCENTS,
                        eCleanVal.STRIP_ALL_PUNCT, eCleanVal.STRIP_INDICATOR_2));
                multiValueExtractor.addCleanVal(eCleanVal.TO_UPPER);
            }
            else if (mapParts[0].equals("titleSortLower"))
            {
                multiValueExtractor.setCleanVal(EnumSet.of(eCleanVal.CLEAN_EACH, eCleanVal.CLEAN_END, eCleanVal.STRIP_ACCCENTS,
                        eCleanVal.STRIP_ALL_PUNCT, eCleanVal.STRIP_INDICATOR_2));
                multiValueExtractor.addCleanVal(eCleanVal.TO_LOWER);
            }
            else if (isAValueMappingConfiguration(mapParts[0]) && joinIndex == -1)
            {
                AbstractMultiValueMapping valueMapping = createMultiValueMapping(mapParts);
                multiValueExtractor.addFormatter(new FieldFormatterMapped(valueMapping));
            }
            else if (isAValueMappingConfiguration(mapParts[0]) && joinIndex != -1)
            {
                // post join map specification
                //    mapping specs before "join" are applied before the join operation
                //    mapping spec that are after "join" are applied to the joined output. 
                //    and are handled elsewhere.
            }
            else
            {
                validationExceptions.add(new IndexerSpecException(origSpec, "Illegal format specification: " + Utils.join(mapParts, " ")));
            }
            currentIndex ++;
        }
        return(joinIndex);

    }

    
    private boolean isAValueMappingConfiguration(final String configuration)
    {
        if (configuration.matches("[A-Z0-9a-z_]+[.]properties([(][A-Za-z0-9]*[)])?") || 
                configuration.matches("[(]this[)][.]properties([(][A-Za-z0-9]*[)])?") || 
                configuration.startsWith("map") || 
                configuration.startsWith("filter") || 
                configuration.startsWith("custom_map"))
        {
            return (true);
        }
        return (false);
    }

    private List<AbstractMultiValueMapping> createMultiValueMappings(String origSpec, List<List<String>> mapSpecs)
    {
        return(createMultiValueMappings(origSpec, mapSpecs, -1));
    }

    /**
     * Creates the multivalue mappers as specified to be appended to a extractor.
     * (given by fieldnames), Where the extractor has already been created in the parser handling code, and the
     * maps definitions following the extractor have been pulled out as a list of lists of strings.
     *
     * @param origSpec
     *            the original index specification (for debugging purposes).
     * @param mapSpecs
     *            the mapping/collector specifications definition for this indexer.
     * @param indexOfJoin 
     *            index within mapSpecs where the "join" specification occurs.
     *            mapping specs before "join" are applied before the join operation
     *            mapping spec that are after "join" are applied to the joined output.           
     * @return an list of multi value mappings
     */
    private List<AbstractMultiValueMapping> createMultiValueMappings(String origSpec, List<List<String>> mapSpecs, int indexOfJoin)
    {
        List<AbstractMultiValueMapping> maps = new ArrayList<AbstractMultiValueMapping>(mapSpecs.size());
        if (mapSpecs.size() == 0)
        {
            return maps;
        }
        int currentIndex = 0;
        for (List<String> mapSpec : mapSpecs)
        {
            if (currentIndex > indexOfJoin)
            {
                String mapParts[] = mapSpec.toArray(new String[0]);
                if (isACollectorConfiguration(mapParts[0]))
                {
                    /* ignore */
                }
                else if (isADecoratorConfiguration(mapParts[0]))
                {
                    /* ignore */
                }
                else if (isAValueMappingConfiguration(mapParts[0]))
                {
                    AbstractMultiValueMapping valueMapping = createMultiValueMapping(mapParts);
                    if (valueMapping != null) maps.add(valueMapping);
                }
                else
                {
                    validationExceptions.add(new IndexerSpecException(origSpec, "Illegal format specification: " + Utils.join(mapParts, " ")));
                }
            }
            currentIndex++;
        }
        return maps;
    }

    
    
    private boolean isACollectorConfiguration(String string)
    {
        if (string.equals("unique") || string.equals("first") || string.equals("sort") || string.equals("notunique") || string.equals("notfirst") || string.equals("all"))
            return(true);
        return(false);
    }

    public MultiValueCollector createMultiValueCollector()
    {
        MultiValueCollector collector = new MultiValueCollector();
        collector.setUnique(this.defaultUniqueVal);
        return(collector);
    }
    
    public MultiValueCollector createMultiValueCollector(List<List<String>> mapSpecs)
    {
        MultiValueCollector collector = new MultiValueCollector();
        collector.setUnique(this.defaultUniqueVal);
        for (List<String> mapSpec : mapSpecs)
        {
            String mapParts[] = mapSpec.toArray(new String[0]);
            
            if (isACollectorConfiguration(mapParts[0]))
            {    
                if (mapParts[0].equals("unique"))
                {
                    collector.setUnique(true);
                }
                else if (mapParts[0].equals("notunique"))
                {
                    collector.setUnique(false);
                }
                else if (mapParts[0].equals("first"))
                {
                    collector.setFirst(mapParts[0]);
                }
                else if (mapParts[0].equals("notfirst"))
                {
                    collector.setFirst(mapParts[0]);
                }
                else if (mapParts[0].equals("all"))
                {
                    collector.setFirst(mapParts[0]);
                }
                else if (mapParts[0].equals("sort"))
                {
                    collector.setSortComparator(mapParts[1], mapParts[2]);
                }
            }
        }
        return collector;
    }

    // currently used by SolrIndexer methods that provide backwards-compatibility 
    public AbstractMultiValueMapping createMultiValueMapping(final String mappingConfig)
    {
        for (final AbstractValueMappingFactory mappingFactory : mappingFactories)
        {
            if (mappingFactory.canHandle(mappingConfig))
            {
                return mappingFactory.createMultiValueMapping(mappingConfig);
            }
        }
        throw new IndexerSpecException("Could not handle impl: " + mappingConfig + "\nLoaded impl factories:\n"
                + mappingFactories.toString().replaceAll(",", ",\n"));
    }

    public AbstractMultiValueMapping createMultiValueMapping(String[] mapParts)
    {
        for (final AbstractValueMappingFactory mappingFactory : mappingFactories)
        {
            if (mappingFactory.canHandle(mapParts[0]))
            {
                return mappingFactory.createMultiValueMapping(mapParts);
            }
        }
        throw new IndexerSpecException("Could not handle map descriptor: " + Utils.join(mapParts, " ")); 
        // + "\nLoaded impl factories:\n" + mappingFactories.toString().replaceAll(",", ",\n"));
    }

    public static String getHomeDir()
    {
        return homeDir;
    }
    
    public static void setHomeDir(String dir)
    {
        homeDir = dir;
    }


}