package org.solrmarc.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
//import org.solrmarc.tools.Utils;
import org.marc4j.marc.Subfield;
import org.marc4j.marc.VariableField;
import org.solrmarc.tools.Utils;

import playground.solrmarc.index.extractor.formatter.FieldFormatterMapped;
import playground.solrmarc.index.extractor.impl.custom.Mixin;
import playground.solrmarc.index.extractor.impl.direct.FieldMatch;
import playground.solrmarc.index.indexer.AbstractValueIndexer;
import playground.solrmarc.index.indexer.ValueIndexerFactory;
import playground.solrmarc.index.mapping.AbstractMultiValueMapping;
import playground.solrmarc.index.specification.AbstractSpecificationFactory;
import playground.solrmarc.index.specification.Specification;

/**
 * class SolrIndexer
 * 
 * This class exists solely for backwards compatibility purposes.  The intention is that if a previous custom function
 * was being used, one that provides the same functionality can be found here.  Furthermore if there were any helper functions
 * that could have been used to create your own custom indexing functions those helper functions should be found here as well.
 * 
 * In most cases the methods found here are merely shims to translate the desired method to use the newer functionality that 
 * is now available.
 * 
 * 
 * @author rh9ec
 *
 */


public class SolrIndexer implements Mixin
{
    static Map<String, Specification> specCache = new HashMap<String, Specification>(); 
    
    public  SolrIndexer() 
    { /* private constructor */ }
    
    static SolrIndexer theSolrIndexer = null;
    
    public static SolrIndexer instance()
    {
        if (theSolrIndexer == null) theSolrIndexer = new SolrIndexer();
        return(theSolrIndexer);
    }
    
    private Specification getOrCreateSpecificationMapped(String tagStr, String map)
    {
        String key = (map == null) ? tagStr : tagStr + map;
        if (specCache.containsKey(key))
        {
            return(specCache.get(key));
        }
        else
        {
            Specification spec = AbstractSpecificationFactory.createSpecification(tagStr);
            if (map != null)
            {
                AbstractMultiValueMapping valueMapping = ValueIndexerFactory.instance().createMultiValueMapping(map);
                spec.addFormatter(new FieldFormatterMapped(valueMapping));
            }
            specCache.put(tagStr,  spec);
            return(spec);
        }
    }
    
    private Specification getOrCreateSpecification(String tagStr, String separator)
    {
        String key = (separator == null) ? tagStr : tagStr + "[" + separator + "]";
        if (specCache.containsKey(key))
        {
            return(specCache.get(key));
        }
        else
        {
            Specification spec = AbstractSpecificationFactory.createSpecification(tagStr);
            if (separator != null)
            {
                spec.setSeparator(separator);
            }
            specCache.put(tagStr,  spec);
            return(spec);
        }
    }
    
    private Specification getOrCreateSpecification(String tagStr, int start, int end)
    {
        String key = (start == -1 && end == -1) ? tagStr : tagStr + "_" + start + "_" + end;
        if (specCache.containsKey(key))
        {
            return(specCache.get(key));
        }
        else
        {
            Specification spec = AbstractSpecificationFactory.createSpecification(tagStr);
            if (start != -1 && end != -1)
            {
                spec.setSubstring(start, end);
            }
            specCache.put(tagStr,  spec);
            return(spec);
        }
    }
    
    /**
     * Get <code>Collection</code> of Strings as indicated by tagStr. For each field 
     * spec in the tagStr that is NOT about bytes (i.e. not a 008[7-12] type fieldspec),  
     * the result string is the concatenation of all the specific subfields.
     * 
     * @param record -
     *            the marc record object
     * @param tagStr
     *            string containing which field(s)/subfield(s) to use. This is a
     *            series of: marc "tag" string (3 chars identifying a marc
     *            field, e.g. 245) optionally followed by characters identifying
     *            which subfields to use. Separator of colon indicates a
     *            separate value, rather than concatenation. 008[5-7] denotes
     *            bytes 5-7 of the 008 field (0 based counting) 100[a-cf-z]
     *            denotes the bracket pattern is a regular expression indicating
     *            which subfields to include. Note: if the characters in the
     *            brackets are digits, it will be interpreted as particular
     *            bytes, NOT a pattern. 100abcd denotes subfields a, b, c, d are
     *            desired.
     * @param collector
     *            object in which to collect the data from the fields described by
     *            <code>tagStr</code>. A <code>Set</code> will automatically de-dupe
     *            values, a <code>List</code> will allow values to repeat. 
     * @throws Exception 
     */
    private void getFieldListCollector(Record record, Specification spec,  Collection<String> collector)
    {
        for (FieldMatch fm : spec.getFieldMatches(record))
        {
            try
            {
                fm.addValuesTo(collector);
            }
            catch (Exception e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return;
    }
    
    public void getFieldListCollector(Record record, String tagStr, String mapStr,  Collection<String> collector)
    {
        Specification spec = getOrCreateSpecificationMapped(tagStr, mapStr);
        getFieldListCollector(record, spec,  collector);
    }

    
    /**
     * Get Set of Strings as indicated by tagStr. For each field spec in the
     * tagStr that is NOT about bytes (i.e. not a 008[7-12] type fieldspec), the
     * result string is the concatenation of all the specific subfields.
     * 
     * @param record -
     *            the marc record object
     * @param tagStr
     *            string containing which field(s)/subfield(s) to use. This is a
     *            series of: marc "tag" string (3 chars identifying a marc
     *            field, e.g. 245) optionally followed by characters identifying
     *            which subfields to use. Separator of colon indicates a
     *            separate value, rather than concatenation. 008[5-7] denotes
     *            bytes 5-7 of the 008 field (0 based counting) 100[a-cf-z]
     *            denotes the bracket pattern is a regular expression indicating
     *            which subfields to include. Note: if the characters in the
     *            brackets are digits, it will be interpreted as particular
     *            bytes, NOT a pattern. 100abcd denotes subfields a, b, c, d are
     *            desired.
     * @return the contents of the indicated marc field(s)/subfield(s), as a set
     *         of Strings.
     * @throws Exception 
     */
    public Set<String> getFieldList(Record record, String tagStr)
    {
        Set<String> result = new LinkedHashSet<String>();
        getFieldListCollector(record, tagStr, null, result);
        return result;
    }
   
    public Set<String> getMappedFieldList(Record record, String tagStr, String mapStr)
    {
        Set<String> result = new LinkedHashSet<String>();
        getFieldListCollector(record, tagStr, mapStr, result);
        return result;
    }

    /**
     * Get <code>List</code> of Strings as indicated by tagStr. For each field spec in the
     * tagStr that is NOT about bytes (i.e. not a 008[7-12] type fieldspec), the
     * result string is the concatenation of all the specific subfields.
     * 
     * @param record -
     *            the marc record object
     * @param tagStr
     *            string containing which field(s)/subfield(s) to use. This is a
     *            series of: marc "tag" string (3 chars identifying a marc
     *            field, e.g. 245) optionally followed by characters identifying
     *            which subfields to use. Separator of colon indicates a
     *            separate value, rather than concatenation. 008[5-7] denotes
     *            bytes 5-7 of the 008 field (0 based counting) 100[a-cf-z]
     *            denotes the bracket pattern is a regular expression indicating
     *            which subfields to include. Note: if the characters in the
     *            brackets are digits, it will be interpreted as particular
     *            bytes, NOT a pattern. 100abcd denotes subfields a, b, c, d are
     *            desired.
     * @return the contents of the indicated marc field(s)/subfield(s).
     * @throws Exception 
     */
    public List<String> getFieldListAsList(Record record, String tagStr) 
    {
        List<String> result = new ArrayList<String>();
        getFieldListCollector(record, tagStr, null, result);
        return result;
    }

    /**
     * Get the first value specified by the tagStr
     * @param record - the marc record object
     * @param tagStr string containing which field(s)/subfield(s) to use. This 
     *  is a series of: marc "tag" string (3 chars identifying a marc field, 
     *  e.g. 245) optionally followed by characters identifying which subfields 
     *  to use.
     * @return first value of the indicated marc field(s)/subfield(s) as a string
     * @throws Exception 
     */
    public String getFirstFieldVal(Record record, String tagStr) 
    {
        Set<String> result = getFieldList(record, tagStr);
        Iterator<String> iter = result.iterator();
        if (iter.hasNext())
            return iter.next();
        else
            return null;
    }

    /**
     * Get the first field value, which is mapped to another value. If there is
     * no mapping for the value, use the mapping for the empty key, if it
     * exists, o.w., use the mapping for the __DEFAULT key, if it exists.
     * @param record - the marc record object
     * @param mapName - name of translation map to use to xform values
     * @param tagStr - which field(s)/subfield(s) to use
     * @return first value as a string
     */
    public String getFirstFieldVal(Record record, String mapName, String tagStr)
    {
        Set<String> result = getMappedFieldList(record, tagStr, mapName);
        Iterator<String> iter = result.iterator();
        return (iter.hasNext())? iter.next() : null;
    }

    public boolean isControlField(String fieldTag)
    {
        if (fieldTag.matches("00[0-9]"))
        {
            return (true);
        }
        return (false);
    }

    /**
     * Get the specified subfields from the specified MARC field, returned as a
     * set of strings to become lucene document field values
     * 
     * @param record     the MARC record object
     * @param fldTag     the field name, e.g. 245
     * @param subfldsStr the string containing the desired subfields
     * @param separator  the separator string to insert between subfield items (if null, a " " will be 
     *                   used)
     * @param collector  an object to accumulate the data indicated by <code>fldTag</code> and 
     *                   <code>subfldsStr</code>.
     */
    public void getSubfieldDataCollector(Record record, String fldTag, String subfldsStr, 
                                                String separator, Collection<String> collector)
    {
        Specification spec = getOrCreateSpecification(fldTag+subfldsStr, separator);
        getFieldListCollector(record, spec, collector);
        return;
    }

    /**
     * Get the specified substring of subfield values from the specified MARC 
     * field, returned as  a set of strings to become lucene document field values
     * @param record - the marc record object
     * @param fldTag - the field name, e.g. 008
     * @param subfldStr - the string containing the desired subfields
     * @param beginIx - the beginning index of the substring of the subfield value
     * @param endIx - the ending index of the substring of the subfield value
     * @param collector  an object to accumulate the data indicated by <code>fldTag</code> and 
     *                   <code>subfldsStr</code>.
     */
    public void getSubfieldDataCollector(Record record, String fldTag, String subfldStr, 
                       int beginIx, int endIx, Collection<String> collector)
    {
        Specification spec = getOrCreateSpecification(fldTag+subfldStr, beginIx, endIx);
        getFieldListCollector(record, spec, collector);
        return;
    }
    
    /**
     * Get the specified subfields from the specified MARC field, returned as a
     * set of strings to become lucene document field values
     * 
     * @param record     the marc record object
     * @param fldTag     the field name, e.g. 245
     * @param subfldsStr the string containing the desired subfields
     * @param separator  the separator string to insert between subfield items 
     *                   (if <code>null</code>, a " " will be used)
     * @return           a Set of String, where each string is the concatenated contents of all the
     *                   desired subfield values from a single instance of the <code>fldTag</code>
     */
    public Set<String> getSubfieldDataAsSet(Record record, String fldTag, String subfldsStr, String separator)
    {
        Set<String> result = new LinkedHashSet<String>();
        getSubfieldDataCollector(record, fldTag, subfldsStr, separator, result);
        return result;
    }
    
    /**
     * Get the specified substring of subfield values from the specified MARC 
     * field, returned as  a set of strings to become lucene document field values
     * @param record    the marc record object
     * @param fldTag    the field name, e.g. 008
     * @param subfldStr the string containing the desired subfields
     * @param beginIx   the beginning index of the substring of the subfield value
     * @param endIx     the ending index of the substring of the subfield value
     * @return          the result set of strings
     */
    public Set<String> getSubfieldDataAsSet(Record record, String fldTag, String subfldStr, int beginIx, int endIx)
    {
        Set<String> result = new LinkedHashSet<String>();
        getSubfieldDataCollector(record, fldTag, subfldStr, beginIx, endIx, result);
        return result;
    }

    /**
     * remove trailing punctuation (default trailing characters to be removed)
     *    See org.solrmarc.tools.Utils.cleanData() for details on the 
     *     punctuation removal
     * @param record marc record object
     * @param fieldSpec - the field to have trailing punctuation removed
     * @return Set of strings containing the field values with trailing
     *         punctuation removed
     */
    public Set<String> removeTrailingPunct(Record record, String fieldSpec)
    {
        Set<String> result = getFieldList(record, fieldSpec);
        Set<String> newResult = new LinkedHashSet<String>();
        for (String s : result)
        {
            newResult.add(Utils.cleanData(s));
        }
        return newResult;
    }

    /**
     * Stub more advanced version of getDate that looks in the 008 field as well as the 260c field
     * this routine does some simple sanity checking to ensure that the date to return makes sense. 
     * @param record - the marc record object
     * @return 260c or 008[7-10] or 008[11-14], "cleaned" per org.solrmarc.tools.Utils.cleanDate()
     */
    
    static AbstractValueIndexer<?> indDate = null;
    
    public String getPublicationDate(final Record record)
    {
        if (indDate == null)
        {
            indDate = ValueIndexerFactory.instance().createValueIndexer("publicationDate = " + 
                "008[7-10]:008[11-14]:260c:264c?(ind2=1||ind2=4),clean, first, " +
                "map(\"(^|.*[^0-9])((20|1[5-9])[0-9][0-9])([^0-9]|$)=>$2\",\".*[^0-9].*=>\")");
        }
        Collection<String> result;
        try
        {
            result = indDate.getFieldData(record);
        }
        catch (Exception e)
        {
            return(null);
        }
        Iterator<String> iter = result.iterator();
        return (iter.hasNext() ? iter.next() : null);
        
//        String field008 = getFirstFieldVal(record, "008");
//        String pubDateFull = getFieldVals(record, "260c", ", ");
//        String pubDateJustDigits = pubDateFull.replaceAll("[^0-9]", "");       
//        String pubDate260c = getDate(record);
//        if (field008 == null || field008.length() < 16) 
//        {
//            return(pubDate260c);
//        }
//        String field008_d1 = field008.substring(7, 11);
//        String field008_d2 = field008.substring(11, 15);
//        String retVal = null;
//        char dateType = field008.charAt(6);
//        if (dateType == 'r' && field008_d2.equals(pubDate260c)) retVal = field008_d2;
//        else if (field008_d1.equals(pubDate260c))               retVal = field008_d1;
//        else if (field008_d2.equals(pubDate260c))               retVal = field008_d2;
//        else if (pubDateJustDigits.length() == 4 && pubDate260c != null &&
//                 pubDate260c.matches("(20|19|18|17|16|15)[0-9][0-9]"))
//                                                                retVal = pubDate260c;
//        else if (field008_d1.matches("(20|1[98765432])[0-9][0-9]"))        
//                                                                retVal = field008_d1;
//        else if (field008_d2.matches("(20|1[98765432])[0-9][0-9]"))        
//                                                                retVal = field008_d2;
//        else                                                    retVal = pubDate260c;
//        return(retVal);
    }

    public Set<String> getFullTextUrls(Record record)
    {
        Set<String> result = new LinkedHashSet<String>();
        Specification spec = getOrCreateSpecification("{856uz3}?((ind1 = 4 || (ind1 = 7 & $x startsWith \"http\")) && (ind2 = 0 || (ind2 = 1 )))", "||");
        getFieldListCollector(record, spec, result);
        return result;
    }

    public Set<String> getSupplUrls(Record record)
    {
        Set<String> result = new LinkedHashSet<String>();
        Specification spec = getOrCreateSpecification("{856uz3}?((ind1 = 4 || (ind1 = 7 & $x startsWith \"http\")) && (ind2 = 2 || (ind2 = 1)))", "||");
        getFieldListCollector(record, spec, result);
        return result;
    }

    /**
     * extract all the subfields requested in requested marc fields. Each
     * instance of each marc field will be put in a separate result (but the
     * subfields will be concatenated into a single value for each marc field)
     * 
     * @param record
     *            marc record object
     * @param fieldSpec -
     *            the desired marc fields and subfields as given in the
     *            xxx_index.properties file
     * @param separator -
     *            the character to use between subfield values in the solr field
     *            contents
     * @return Set of values (as strings) for solr field
     */
    public Set<String> getAllSubfields(final Record record, String fieldSpec, String separator)
    {
        Set<String> result = new LinkedHashSet<String>();
        Specification spec = getOrCreateSpecification(fieldSpec, separator);
        getFieldListCollector(record, spec, result);
        return result;
    }
  
    /**
     * extract all the subfields requested in requested marc fields. Each
     * instance of each marc field will be put in a separate result (but the
     * subfields will be concatenated into a single value for each marc field)
     * 
     * @param record
     *            marc record object
     * @param fieldSpec -
     *            the desired marc fields and subfields as given in the
     *            xxx_index.properties file
     * @param separator -
     *            the character to use between subfield values in the solr field
     *            contents
     * @return Set of values (as strings) for solr field
     */
    public List<String> getAllSubfieldsAsList(final Record record, String fieldSpec, String separator)
    {
        List<String> result = new ArrayList<String>();
        Specification spec = getOrCreateSpecification(fieldSpec, separator);
        getFieldListCollector(record, spec, result);
        return result;
    }

    /**
     * Loops through all datafields and creates a field for "all fields"
     * searching. Shameless stolen from Vufind Indexer Custom Code
     * 
     * @param record
     *            marc record object
     * @param lowerBoundStr -
     *            the "lowest" marc field to include (e.g. 100). defaults to 100
     *            if value passed doesn't parse as an integer
     * @param upperBoundStr -
     *            one more than the "highest" marc field to include (e.g. 900
     *            will include up to 899). Defaults to 900 if value passed
     *            doesn't parse as an integer
     * @return a string containing ALL subfields of ALL marc fields within the
     *         range indicated by the bound string arguments.
     */
    public String getAllSearchableFields(final Record record, String lowerBoundStr, String upperBoundStr)
    {
        StringBuffer buffer = new StringBuffer("");
        int lowerBound = localParseInt(lowerBoundStr, 100);
        int upperBound = localParseInt(upperBoundStr, 900);

        List<DataField> fields = record.getDataFields();
        for (DataField field : fields)
        {
            // Get all fields starting with the 100 and ending with the 839
            // This will ignore any "code" fields and only use textual fields
            int tag = localParseInt(field.getTag(), -1);
            if ((tag >= lowerBound) && (tag < upperBound))
            {
                // Loop through subfields
                List<Subfield> subfields = field.getSubfields();
                for (Subfield subfield : subfields)
                {
                    if (buffer.length() > 0)
                        buffer.append(" ");
                    buffer.append(subfield.getData());
                }
            }
        }
        return buffer.toString();
    }

    /**
     * Loops through all datafields and creates a field for "all fields"
     * searching. Shameless stolen from Vufind Indexer Custom Code
     * 
     * @param record
     *            marc record object
     * @param lowerBoundStr -
     *            the "lowest" marc field to include (e.g. 100). defaults to 100
     *            if value passed doesn't parse as an integer
     * @param upperBoundStr -
     *            one more than the "highest" marc field to include (e.g. 900
     *            will include up to 899). Defaults to 900 if value passed
     *            doesn't parse as an integer
     * @return a Set of strings containing ALL subfields of ALL marc fields within the
     *         range indicated by the bound string arguments, with one string for each field encountered.
     */
    public Set<String> getAllSearchableFieldsAsSet(final Record record, String lowerBoundStr, String upperBoundStr)
    {
        Set<String> result = new LinkedHashSet<String>();
        int lowerBound = localParseInt(lowerBoundStr, 100);
        int upperBound = localParseInt(upperBoundStr, 900);

        List<DataField> fields = record.getDataFields();
        for (DataField field : fields)
        {
            // Get all fields starting with the 100 and ending with the 839
            // This will ignore any "code" fields and only use textual fields
            int tag = localParseInt(field.getTag(), -1);
            if ((tag >= lowerBound) && (tag < upperBound))
            {
                // Loop through subfields
                StringBuffer buffer = new StringBuffer("");
                List<Subfield> subfields = field.getSubfields();
                for (Subfield subfield : subfields)
                {
                    if (buffer.length() > 0)
                        buffer.append(" ");
                    buffer.append(subfield.getData());
                }
                result.add(buffer.toString());
            }
        }
        return result;
    }
    /**
     * return an int for the passed string
     * @param str
     * @param defValue - default value, if string doesn't parse into int
     */
    private int localParseInt(String str, int defValue)
    {
        int value = defValue;
        try
        {
            value = Integer.parseInt(str);
        }
        catch (NumberFormatException nfe)
        {
            // provided value is not valid numeric string
            // Ignoring it and moving happily on.
        }
        return (value);
    }

    public List<VariableField> getFieldSetMatchingTagList(Record record, String tagList)
    {
        String tags[] = tagList.split(":");
        return(record.getVariableFields(tags));
    }
    
}
