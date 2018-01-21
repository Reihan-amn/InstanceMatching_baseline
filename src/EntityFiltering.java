import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;



public class EntityFiltering {

    static HashMap<String, HashSet<String>> individualsCache;
    static HashMap<String, HashSet<CruiseProperty>> distinctPropertiesCache;
    static HashMap<String, Integer> usageCache = new HashMap<>();
    static HashMap<String, Integer> distinctValuesCache = new HashMap<>();

    static HashSet<String> cruiseProperties;
    static String activeQuery; // used for debugging
    static String sparqlEndpoint;

    public EntityFiltering(){
        sparqlEndpoint = "http://data.geolink.org/sparql";
    }
    public static ResultSet query(String sparqlQuery) {
        activeQuery = sparqlQuery;
        Logger.getGlobal().severe(activeQuery);
        QueryEngineHTTP httpQuery = new QueryEngineHTTP(sparqlEndpoint, sparqlQuery);
        return httpQuery.execSelect();
    }

    public static HashSet<CruiseProperty> getPropeties(String type, boolean incoming) {
		/* this method returns the set of CruiseProperty
		* (only name of property,
		* the data type of the value,
		* whether it includes incoming or not)
		*/

//		if (distinctPropertiesCache.containsKey(type)) {
//			return distinctPropertiesCache.get(type);
//
//		} else {


        HashSet<CruiseProperty> properties = new HashSet<>();

        // outgoing properties (i.e. object or datatype properties in which
        // this individual is in the domain)
        String sparqlQuery = "" +
                "SELECT DISTINCT ?predicate ?predType \n" +
                "WHERE { \n" +
                "?individual a <" + type + "> . \n" +
                "?individual ?predicate ?object . \n" +
                "?object a ?predType \n" +
                "}";

        ResultSet results = query(sparqlQuery);

        int count = 0;
        while (results.hasNext()) {
            count +=1;
            QuerySolution solution = results.next();
            RDFNode predicate = solution.get("predicate");
            RDFNode predType = solution.get("predType");
            CruiseProperty prop = new CruiseProperty(predicate.toString());
            prop.incoming = false;
            String dataType;
            if(predType.toString().contains("#")){ // anything after # is the data type otherwise the uri is the datatype itself
                dataType = predType.toString().substring(predType.toString().lastIndexOf("#")+1);
            }
            else{
                dataType = predType.toString();
            }
            prop.datatype = dataType;
            System.out.print("\ncount " + count);
            properties.add(prop);
            System.out.print("\nsize: " + properties.size());
            System.out.print("\npred: " + predicate.toString() + " datatype: " + dataType);
        }


        if (incoming) {

            // incoming properties (i.e. object properties in which this individual
            // is in the range)
            // if incoming is true, in addition to all result that are coming from the <individual, propery, value>
            // we need to query for <object, property, individual>

            sparqlQuery = "" +
                    "SELECT DISTINCT ?predicate ?predType \n" +
                    "WHERE { \n" +
                    "?individual a <" + type + "> . \n" +
                    "?subject ?predicate ?individual . \n" +
                    "?subject a ?predType \n" +
                    "}";

            results = query(sparqlQuery);

            while (results.hasNext()) {
                QuerySolution solution = results.next();
                RDFNode predicate = solution.get("predicate");
                RDFNode predType = solution.get("predType");
                CruiseProperty prop = new CruiseProperty(predicate.toString());
                prop.incoming = true;
                String dataType;
                if(predType.toString().contains("#")){ // anything after # is the data type
                    dataType = predType.toString().substring(predType.toString().lastIndexOf("#")+1);
                }
                else{
                    dataType = predType.toString();
                }
                prop.datatype = dataType;
                System.out.print("\ncount " + count);
                properties.add(prop);
                System.out.print("\nsize: " + properties.size());
                System.out.print("\npred: " + predicate.toString() + " datatype: " + dataType);

            }
        }

        //		distinctPropertiesCache.put(type, properties);

        return properties;
    }
    public HashSet<CruiseProperty> getBestCoverage_DiscProperties(String type, double functionalThreshold, int propertyLimit, boolean includeIncoming) {
        //HashMap<String, HashSet<Property>>
        HashMap<String, HashSet<CruiseProperty>> propertiesByType = new HashMap<>();

        // get all of the properties for which an individual of this type is in the domain or range
        HashSet<CruiseProperty> propertySet = getPropeties(type, includeIncoming);

        //coverage - discriminability
        // For each of these properties, figure out how many times that property is used (usage)
        // and how many unique values it takes. The ratio of these is used to determine if this
        // property should be used to compare two individuals of this type.
        // Then by using the selected properties from here and other data structured in this project, we maintain the comparison
        // For example, if a datatype contains very few unique values (such as "hasTitle" which can only be one of
        // Mr, Mrs, Ms, or Dr) then it is not very useful. On the other hand, even if a property
        // contains many diverse values, if that property is seldom present for individuals of
        // this type, then it is still not very useful. The cullProperties method removes
        // properties with a ratio of unique values to usage lower than the functionalThreshold.
        // It then removes properties with low usage, until the number remaining is less than
        // the propertyLimit (a high propertyLimit will result in a longer runtime).

        for (CruiseProperty property : propertySet) {
            Logger.getGlobal().info("Calculating usgae statistics for " + property.name);
            property.used = getPropertyUsage(property, type);
            System.out.print("________________________________________");
            property.unique = getPropertyUniqueness(property, type);
        }

        Logger.getGlobal().info(propertySet.size() + " properties before culling");
        Logger.getGlobal().info("Culling properties");

        // based of property usage and coverage, select the top n of them in cullProperty method
        propertySet = cullProperties(propertySet, functionalThreshold, propertyLimit);
        propertiesByType.put(type, propertySet);
        Logger.getGlobal().info(propertySet.size() + " properties after culling");


        Logger.getGlobal().info("The properties to compare for individuals of this type are:");
        for (CruiseProperty property : propertySet) {
            System.out.print("\n" + property.name + " (" + property.datatype + ") " +
                    property.used + " " + property.unique +
                    " " + (property.unique / (double) property.used));
        }
        Logger.getGlobal().info("\n\n");

        return  propertySet;
    }
    public static int getPropertyUsage(CruiseProperty property, String type) {

        String sparqlQuery = "";
        int count = 0;
        ResultSet results = null;

        sparqlQuery = "" +
                "SELECT (COUNT (DISTINCT ?individual) as ?count) \n" +
                "WHERE { \n" +
                "?individual a <" + type + "> .\n" +
                "?individual <" + property.name + "> ?o \n" +
                "}";

        results = query(sparqlQuery);

        if (results.hasNext()) {  // the output of the query is just one single number, if that is empty, it means nothing has been returned and count was 0

            QuerySolution solution = results.next();
            RDFNode answer = solution.get("count");
            usageCache.put(sparqlQuery, answer.asLiteral().getInt());
            count = answer.asLiteral().getInt();

        } else {
            count = 0;
        }

        if (property.incoming) {

            sparqlQuery = "" +
                    "SELECT (COUNT (DISTINCT ?individual) as ?count) \n" +
                    "WHERE { \n" +
                    "?individual a <" + type + "> .\n" +
                    "?subject <" + property.name + "> ?individual \n" +
                    "}";

            results = query(sparqlQuery);

            if (results.hasNext()) {  // the output of the query is just one single number, if that is empty, it means nothing has been returned and count was 0

                QuerySolution solution = results.next();
                RDFNode answer = solution.get("count");
                usageCache.put(sparqlQuery, answer.asLiteral().getInt());
                count = count + answer.asLiteral().getInt();

            } else {
                count = count + 0;
            }

        }

        return count;



//		if (usageCache.containsKey(sparqlQuery)) {
//			return usageCache.get(sparqlQuery);
//
//		} else {


        //}
    }
    public static int getPropertyUniqueness(CruiseProperty property, String type) {

        String sparqlQuery = "";
        int count = 0;
        ResultSet results = null;

        sparqlQuery = "" +
                "SELECT (COUNT (DISTINCT ?o) as ?count) \n" +
                "WHERE { \n" +
                "?individual a <" + type + "> .\n" +
                "?individual <" + property.name + "> ?o \n" +
                "}";
        results = query(sparqlQuery);

        if (results.hasNext()) {

            QuerySolution solution = results.next();
            RDFNode answer = solution.get("count");
            distinctValuesCache.put(sparqlQuery, answer.asLiteral().getInt());
            count = answer.asLiteral().getInt();

        } else {
            count =  0;
        }

        if (property.incoming) {

            sparqlQuery = "" +
                    "SELECT (COUNT (DISTINCT ?s) as ?count) \n" +
                    "WHERE { \n" +
                    "?individual a <" + type + "> .\n" +
                    "?s <" + property.name + "> ?individual \n" +
                    "}";
            results = query(sparqlQuery);

            if (results.hasNext()) {

                QuerySolution solution = results.next();
                RDFNode answer = solution.get("count");
                distinctValuesCache.put(sparqlQuery, answer.asLiteral().getInt());
                count = count + answer.asLiteral().getInt();

            } else {
                count =  count + 0;
            }

        }
        return count;




//		if (distinctValuesCache.containsKey(sparqlQuery)) {
//			return distinctValuesCache.get(sparqlQuery);
//
//		} else {


        //	}
    }
    public static HashSet<CruiseProperty> cullProperties(HashSet<CruiseProperty> properties,
                                                         double threshold, int limit) {

        HashSet<CruiseProperty> smallerSet = new HashSet<>();

        double bestStrength = 0.0;

        //finding the best strength
        for (CruiseProperty prop: properties) {
            double strength = Math.min(1, prop.unique / (double) prop.used);
            if (strength > bestStrength) {
                bestStrength = strength;
            }
        }

        //cutoff is equals to best strength but because all the values drop bellow it, we added a threshold to keep those in this threshold (0.3<<0.5)
        double cutoff = bestStrength * threshold;

        ArrayList<CruiseProperty> keep = new ArrayList<>();

        // keeping those properties with good unigueness and coverage ratio only
        for (CruiseProperty prop: properties) {
            double strength = Math.min(1, prop.unique / (double) prop.used);
            if (strength >= cutoff) {
                keep.add(prop);
            }
        }

        if (keep.size() > limit) {
            keep.sort(new Comparator<CruiseProperty>() {
                public int compare(CruiseProperty p1, CruiseProperty p2) {
                    return p2.used - p1.used;
                }});
            for (int i=0; i<limit; i++) {
                smallerSet.add(keep.get(i));
            }
        }else{
            for (CruiseProperty k: keep){
                smallerSet.add(k);
            }

        }

        return smallerSet;
    }



    //    public static void main(String[] args){
//
//        long startTime = System.currentTimeMillis();
//        String type = "http://schema.geolink.org/1.0/base/main#Cruise";
//
//
//        // get all of the properties for which an individual of this type is in the domain or range
//        HashSet<CruiseProperty> propertySet = getPropeties(type, true);
//        System.out.print("\n test:     " +  propertySet.size());
//        for(CruiseProperty cp : propertySet){
//            System.out.print(cp.getName() + " type  "+ cp.datatype + "\n");
//        }
//
//        long endTime   = System.currentTimeMillis();
//        long totalTime = (endTime - startTime)/(1000*60);
//        System.out.println(totalTime + " in minutes");
//
//    }

}

