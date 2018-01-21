import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.logging.Logger;

public class CruiseEntityProposer implements Serializable {

    static HashSet<String> cruiseProperties;
    static String activeQuery; // used for debugging
    static String sparqlEndpoint = "http://data.geolink.org/sparql";

    //defining general data structures
    static ArrayList<String> individualsCache;
    static HashMap<String, HashSet<CruiseProperty>> individuals_property_Cache;
    static HashMap<String, HashSet<CruiseProperty>> best_unique_coverage_individuals_property_Cache;
    static HashMap<String, ArrayList<String>>  same_label_Cache;
    static HashMap<String, HashSet<CruiseProperty>> common_property_Cache;
    static HashMap<String, HashSet<CruiseProperty>> common_value_Cache;
    static HashMap<String, HashSet<CruiseProperty>> common_PropertyValue_Cache;


    private static ResultSet query(String sparqlQuery) {
        activeQuery = sparqlQuery;
        Logger.getGlobal().severe(activeQuery);
        QueryEngineHTTP httpQuery = new QueryEngineHTTP(sparqlEndpoint, sparqlQuery);
        return httpQuery.execSelect();
    }
    public static void getIndividuals(String type) throws Exception {
        /*
        * this function queries for all the individuals of referred type
        * */

        // load what is already in the cache into ArrayList<String> individualCache
        readCache_Individuals();

        try{
            String sparqlQuery = "" + "SELECT ?individual \n"
                    + "WHERE { \n"
                    // + "?individual a <" + type + ">\n}";
                    + "?individual a <" + type + ">\n"
                    + "} LIMIT 2.";

            ResultSet results = query(sparqlQuery);

            while (results.hasNext()) {
                QuerySolution solution = results.next();
                RDFNode answer = solution.get("individual");
                String individual = answer.toString();
                if(individualsCache.contains(individual)) continue;
                else {
                    individualsCache.add(individual);
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
            System.err.println("I will try to write out the individual property cache!");
            writeCache_Individuals();
        }
        // loading the cache with what we have in individualCache
        writeCache_Individuals();
    }
    public static void getIndivisualProperties() throws Exception {

    /*
    * this function queries for all individuals from individualCache and run Sparql query to extract the label and
    * all property-value of them
    */

        // saving all individuals from individualcache to individualsCache structure for query
        readCache_Individuals();

        // read in what we have so far.. (which individuals we already queried for)
        readCache_Individuals_Property();

        int count = 0;
        for (String individual : individualsCache) {

            if (count++ % 100 == 0) { // this is just to see how things are going...
                System.out.println(count + "  Cruise Processing ... \n");
            }

            // if this individual is already in the cache, continue on to the next one
            if (individuals_property_Cache.containsKey(individual)) continue;

            try {
                // (for all properties of a certain individual create a HashMap of<String, cruisePorperty>
                //this is for keeping all the property in one data structure for a easy search.

                HashMap<String, CruiseProperty> propertySet = new HashMap<>();
                HashSet<CruiseProperty> properties = new HashSet<>(); // all properties of this individual

                //this query returns the properties and their values and the value label if there is any/we don't care about propertyLabel
                //because one sameAs properties have label as "sameAs". We try to extract property labels as the string after the last #
                //reason for union is to cover all the individuals that has a label for their value or those that dont have

                String sparqlQuery = "" + "SELECT ?p ?value ?valueLabel \n"
                        + "WHERE { \n"
                        + "{<" + individual + "> ?p ?value. \n"
                        + "?value rdfs:label ?valueLabel.} \n"
                        + "UNION \n"
                        + "{<" + individual + "> ?p ?value.} \n"
                        + "}";

                ResultSet results = query(sparqlQuery);

                while (results.hasNext()) { //this loop traverse all the results one by one
                    QuerySolution qs = results.next();
                    String p = qs.get("p").toString(); // property uri such as http://www.w3.org/2002/07/owl#sameAs

                    String value = qs.get("value").toString();

                    if(p.contains("#")){ // anything after # is the label
                        p = p.substring(p.lastIndexOf("#")+1);
                    }
                    if (value.contains("^^")) { //
                        value = value.substring(0, value.indexOf("^^"));
                    }
                    if (value.contains("@")) { // taking the language code out of the value
                        value = value.substring(0, value.indexOf("@"));
                    }
                    String valueLabel;
                    if (qs.get("valueLabel") != null) {
                        valueLabel = qs.get("valueLabel").toString();
                        if (valueLabel.contains("^^")) {
                            valueLabel = valueLabel.substring(0, valueLabel.indexOf("^^"));
                        }
                        if (valueLabel.contains("@")) {
                            valueLabel = valueLabel.substring(0, valueLabel.indexOf("@"));
                        }
                    } else {
                        valueLabel = value; // if it doesn't have the value label, then the uri itself is the value
                    }

                    if (propertySet.containsKey(p)) {
                        // if already this property is on the propValues list, just add the value to the list of values
                        propertySet.get(p).addValue(value, valueLabel);
                    } else {
                        // creating a new property
                        CruiseProperty cProperty = new CruiseProperty(p);
                        cProperty.setValue(value, valueLabel);
//                        System.out.println("cProp test  \n" + cProperty.getName() + "  \n" +cProperty.getValue() );
                        propertySet.put(p, cProperty); // property uri + property
//                        System.out.println("what was added to propertySet: " +                      propertySet.get(cProperty.getName()).getValue());

                    }
//                    System.out.print("\n_________________________________TEST%%%%%%%%%%%%%\n");
//                    for (String str : propertySet.keySet()) {
//                        System.out.println(str);
//                        System.out.println(propertySet.get(str).getValue());  //weird working and returnin the value of the last result of the query
//                    }
                    properties.add(propertySet.get(p));  //adding this property and their values to the hashset of actual properties
                    // next result
                }
                //when results ended we add them to indiv_prop data structure and go to the next individual
                individuals_property_Cache.put(individual, properties);

            } catch (Exception e) {

                e.printStackTrace();
                System.err.println("I will try to write out the individual property cache!");

                // if anything went wrong, write out the cache so that we don't have to start from scratch next time
                writeCache_Individuals_Property();
            }
        }
        // after all write the cache of individuals and their properties
        writeCache_Individuals_Property();
    }



    public HashMap<String, HashSet<CruiseProperty>> all_individuals_proposer(){

        return null;
    }
    public static HashMap<String, ArrayList<String>> same_label_proposer(HashMap<String, HashSet<CruiseProperty>> indiv_Prop) throws Exception {

        HashMap<String, ArrayList<String>> matches = new HashMap<String, ArrayList<String>>();

        for (String entUri : indiv_Prop.keySet()) {
            //System.out.println("uri: "+ entUri);
            HashSet<CruiseProperty> cp = indiv_Prop.get(entUri); //get all properties of this uri
            for (CruiseProperty ccp : cp) {
//                System.out.println("prop name: " + ccp.getName());
                if(ccp.getName().contains("label")){  // only consider those that have property label
                    System.out.println("working on label:   " + ccp.getValue().toString());
                    if(matches.containsKey(ccp.getValue().toString()))
                    {
                        matches.get(ccp.getValue().toString()).add(entUri);
                    }
                    else{
                        ArrayList<String> tempUris =  new ArrayList<>();
                        tempUris.add(entUri);
                        //key is the value of the label and value is the arraylist of string of uri
                        matches.put(ccp.getValue().toString(), tempUris);
                    }
                }
            }
        }
        //extracting labels with more than two matched uris
        HashMap<String, ArrayList<String>> matches_final = new HashMap<String, ArrayList<String>>();
        for(String label: matches.keySet()){
            if(matches.get(label).size() > 1 ){
                matches_final.put(label, matches.get(label));
            }
        }

        writeCache_SameLabel(matches_final);
        return matches_final;
    }
    public static ArrayList<ArrayList<String>> same_peopertyName_proposer(HashMap<String, HashSet<CruiseProperty>> indiv_Prop) throws Exception {

        ArrayList<ArrayList<String>> matches = new ArrayList<>();
        HashMap<String, HashSet<CruiseProperty>> indiv_Prop_copy = new HashMap<>(indiv_Prop);
        ArrayList<String> uris_A = new ArrayList<>();
        ArrayList<String> uris_B = new ArrayList<>();

        //converting set of key to list of key
        uris_A.addAll(indiv_Prop.keySet());
        uris_B = uris_A;

        int count = 0;

        //iterating over the 2 hashmaps
        for(int i =0 ; i <uris_A.size() ; i++){
            //uri_1
            String entity_uri1 = uris_A.get(i);
            //properties of uri_1
            HashSet<String> propery_name_set_1 = new HashSet<>();
            for(CruiseProperty cc_1: indiv_Prop.get(entity_uri1)){
                propery_name_set_1.add(cc_1.getName());
            }
            for (int j=i+1 ; j <uris_B.size() ; j++){
                count++;
                //uri_2
                String entity_uri2 = uris_B.get(j);
                //properties of uri_2
                HashSet<String> propery_name_set_2 = new HashSet<>();
                for(CruiseProperty cc_2: indiv_Prop.get(entity_uri2)){
                    propery_name_set_2.add(cc_2.getName());
                }
                //check if two sets of propery-name have any overlap
                HashSet<String> intersection = new HashSet<String>(propery_name_set_1);
                intersection.retainAll(propery_name_set_2);

                if(intersection.size() != 0){
                    ArrayList<String> candidate_pair = new ArrayList<>(2);
                    candidate_pair.add(entity_uri1);
                    candidate_pair.add(entity_uri2);
                    matches.add(candidate_pair);
                }

                //test print
                System.out.print("   proposed matches on property name:  uri1: "   + entity_uri1 + "   uri2: " + entity_uri2 + "   common property " + intersection +  "\n ");
            }
        }
        writeCache_SamePropertyName(matches);
        System.out.println("number of all pairs:  " + matches.size());
        return matches;
    }
    public static ArrayList<ArrayList<String>> same_property_Name_Value_proposer(HashMap<String, HashSet<CruiseProperty>> indiv_Prop) throws Exception {

        ArrayList<ArrayList<String>> matches = new ArrayList<>();
        ArrayList<String> uris_A = new ArrayList<>();
        ArrayList<String> uris_B = new ArrayList<>();

        //converting set of key to list of key
        uris_A.addAll(indiv_Prop.keySet());
        uris_B = uris_A;

        //iterating over the 2 hashmaps
        int count = 0;
        for(int i =0 ; i <uris_A.size() ; i++){
            count ++;
//            nextPair:
//            {
//                System.out.println("\nnew pair______________________________  i: " + i + "  " );
            //uri_1
            String entity_uri1 = uris_A.get(i);
            //properties of uri_1
            HashSet<String> propery_name_set_1 = new HashSet<>();
//            HashSet<PropertyValue> property_value_set_1 = new HashSet<>();
            for (CruiseProperty cc_1 : indiv_Prop.get(entity_uri1)) {
                propery_name_set_1.add(cc_1.getName());
            }
//                System.out.print("\n===" + propery_name_set_1.size() + "\n");

            for (int j = i + 1 ; j < uris_B.size(); j++) {
//                    System.out.println("\nnew pair______________________________  j: " + j + "  " );
                boolean common_value = false;
                //uri_2
                String entity_uri2 = uris_B.get(j);
                //properties of uri_2
                HashSet<String> propery_name_set_2 = new HashSet<>();
                for (CruiseProperty cc_2 : indiv_Prop.get(entity_uri2)) {
                    propery_name_set_2.add(cc_2.getName());
                }

//                    System.out.print("\n==second  " + propery_name_set_2.size() + "\n");
                //check if two sets of propery-name have any overlap
                HashSet<String> intersection = new HashSet<String>(propery_name_set_1);
                intersection.retainAll(propery_name_set_2);

//                    System.out.print("\n entity 1 " + entity_uri1);
//                    System.out.print("\n entity 2 " + entity_uri2);
                //if 2 entities have any property in common
                if (intersection.size() != 0) {
//                        System.out.print("\n   common prop set:    " + intersection.toString());

                    for (String properyName : intersection) {
                        String commonProperty = properyName;
                        HashSet<PropertyValue> value_set_1 = new HashSet<>();
                        HashSet<PropertyValue> value_set_2 = new HashSet<>();
                        // taking the value set for this common property
                        for (CruiseProperty cc_1 : indiv_Prop.get(entity_uri1)) {
                            if (cc_1.getName().equals(commonProperty)) {
                                value_set_1 = new HashSet<>(cc_1.getValue());
                            }
                        }
                        for (CruiseProperty cc_2 : indiv_Prop.get(entity_uri2)) {
                            if (cc_2.getName().equals(commonProperty)) {
                                value_set_2 = new HashSet<>(cc_2.getValue());
                            }
                        }
                        HashSet<PropertyValue> intersection_value = new HashSet<PropertyValue>(value_set_1);
                        intersection_value.retainAll(value_set_2);
//                            System.out.print("\n\n for this property   " + commonProperty);
//                            System.out.print("\n   value set 1" + value_set_1);
//                            System.out.print("\n   value set 2" + value_set_2);


                        //if the value set for one common propert is not empty go to next pair of entities
                        if (intersection_value.size() != 0) {
//                                System.out.print("\n" + "common values : " + intersection_value);

                            ArrayList<String> candidate_pair = new ArrayList<>(2);
                            candidate_pair.add(entity_uri1);
                            candidate_pair.add(entity_uri2);
                            matches.add(candidate_pair);
//                                System.out.print("\nMatches ++ " + matches.size());
                            common_value = true;
//                                System.out.print("before break");
                            //as soon as finding a common value for a property, this pair will be added
                            break; // compare next two entities if they have a value in common
                        }

                    } //for each property name
                }
                //test print after break
                System.out.print("       " + i + "     " + j + "     " + entity_uri1 + "   " + entity_uri2 + "    " + intersection + "\n ");
//                    System.out.print("\n" + propery_name_set_1.size() + " " + propery_name_set_2.size() + "  " + intersection.size() + "  " + "\n");
            }
//            } //next pair
        }
        writeCache_SamePropertyName_Value(matches);
        return matches;

    }
    public static ArrayList<ArrayList<String>> same_peopertyValue_proposer(HashMap<String, HashSet<CruiseProperty>> indiv_Prop) throws Exception {

        ArrayList<ArrayList<String>> matches = new ArrayList<>();
        HashMap<String, HashSet<CruiseProperty>> indiv_Prop_copy = new HashMap<>(indiv_Prop);
        ArrayList<String> uris_A = new ArrayList<>();
        ArrayList<String> uris_B = new ArrayList<>();

        //converting set of key to list of key
        uris_A.addAll(indiv_Prop.keySet());
        uris_B = uris_A;

        //iterating over the 2 hashmaps
        for(int i =0 ; i <uris_A.size() ; i++){
            //uri_1
            String entity_uri1 = uris_A.get(i);
            //all values of the properties of uri_1
            HashSet<String> value_set_1 = new HashSet<>();
            for(CruiseProperty cc_1: indiv_Prop.get(entity_uri1)){
                for(PropertyValue pv: cc_1.getValue()){
                    value_set_1.add(pv.toString());
                }
            }
            for (int j=i+1 ; j <uris_B.size() ; j++){
                //uri_2
                String entity_uri2 = uris_B.get(j);
                //all values of the properties of uri_2
                HashSet<String> value_set_2 = new HashSet<>();
                for(CruiseProperty cc_2: indiv_Prop.get(entity_uri2)){
                    for(PropertyValue pv: cc_2.getValue()){
                        value_set_2.add(pv.toString());
                    }                }
                //check if two sets of propery-name have any overlap
                HashSet<String> intersection = new HashSet<String>(value_set_1);
                intersection.retainAll(value_set_2);

                if(intersection.size() != 0){
                    ArrayList<String> candidate_pair = new ArrayList<>(2);
                    candidate_pair.add(entity_uri1);
                    candidate_pair.add(entity_uri2);
                    matches.add(candidate_pair);
                }

                //test print
                System.out.print("   entity 1  " +entity_uri1 + "   entity 2  " + entity_uri2 + "   intersection  " + intersection +  "\n ");
//                System.out.print("\n"+ value_set_1.size() +" " + value_set_2.size() + "  " + intersection.size() +  "  "+ "\n");

            }
        }
        writeCache_SamePropertyValue(matches);
        return matches;
    }
    public static HashMap<String, HashSet<CruiseProperty>> best_uniqCov_property_set_proposer(HashMap<String, HashSet<CruiseProperty>> individuals_property_Cache, String type,double threshold, int limit, boolean incoming) throws Exception {
        /* this is different type of proposer and instead of proposing the property pairs,
         * this propose the hashmap of the uris and their corresponding properties
         * but these properties are selected by best-coverage-uniqueness algorithm
        */
        // filtering the properties and get the best set of properties based on uniqueness and coverage
        EntityFiltering ef = new EntityFiltering();
        HashSet<CruiseProperty> selectedProperties =  new HashSet<>();
        selectedProperties = ef.getBestCoverage_DiscProperties(type, threshold, limit, incoming);

        // extracting selected property name for further analysis
        ArrayList<String> selected_properties_name = new ArrayList<>();
        for (CruiseProperty prop: selectedProperties){
            selected_properties_name.add(prop.getName());

            //adding the exact property name which appear after # to increase the chance of catching more matches (doubling the list)
            if(prop.getName().toString().contains("#")){ // anything after # is the data type
                selected_properties_name.add(prop.getName().toString().substring(prop.getName().toString().lastIndexOf("#")+1));
            }
        }

        // processing the individuals_property_Cache hashmap to clean the set of properties to a smaller set based on filtered properties
        best_unique_coverage_individuals_property_Cache = new HashMap<>();


        int ccount = 0;
        for(String uri: individuals_property_Cache.keySet()){
            System.out.print(uri + "  ++++++++++++++++++++++++++++++++++++\n");
            HashSet<CruiseProperty> subset_properties = new HashSet<>();
            for(CruiseProperty props: individuals_property_Cache.get(uri)){
                String prop_name = props.getName();
                if(selected_properties_name.contains(prop_name)){
                    // if this property is inside the selected property set, then add it
                    subset_properties.add(props);
                    ccount +=1;
                }
            }
            best_unique_coverage_individuals_property_Cache.put(uri, subset_properties);
        }
        //print

        for(String s : best_unique_coverage_individuals_property_Cache.keySet()){
            System.out.print("\n uri:    "+s+"\n");
            for(CruiseProperty props: best_unique_coverage_individuals_property_Cache.get(s)){
                System.out.print("--------------------"+ "\n");
                System.out.print(props.getName());
                System.out.print("\n"+props.getValue());
            }
        }

        writeCache_best_uniqCov(best_unique_coverage_individuals_property_Cache);
        return best_unique_coverage_individuals_property_Cache;
    }

    private static void writeCache_Individuals() throws Exception {
        try {
            System.out.print("\n writing in individual cache...");
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("Cache/Individuals.dat"));
            oos.writeObject(individualsCache);
            oos.close();
        } catch(Exception e){
            System.out.print("\n cannot write in individual cache/ may be path directory is not found...");
        }
    }
    private static void readCache_Individuals() {

        ObjectInputStream ois = null;
        individualsCache = new ArrayList<>();

        try {
            System.out.print("\n reading from individual cache...");
            ois = new ObjectInputStream(new FileInputStream("Cache/Individuals.dat"));
            individualsCache = (ArrayList<String>) ois.readObject();
            ois.close();
        } catch (Exception e) {
            System.out.print("\n cannot read from individual cache...");
            individualsCache = new ArrayList<>();}
    }
    private static void writeCache_Individuals_Property() throws Exception {

        try {
            System.out.print("\n writing in individual_property cache...");
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("Cache/Individual_Properties.dat"));
            oos.writeObject(individuals_property_Cache);
            oos.close();
        } catch(Exception e){
            System.out.print("\n cannot write in individual_property cache...");
        }
    }
    private static void readCache_Individuals_Property() {

        ObjectInputStream ois = null;
        individuals_property_Cache = new HashMap<>();
        try {
            System.out.print("\n reading from individual_property cache...");
            ois = new ObjectInputStream(new FileInputStream("Cache/Individual_Properties.dat"));
            individuals_property_Cache = (HashMap<String, HashSet<CruiseProperty>>) ois.readObject();
            ois.close();
        } catch (Exception e) {
            System.out.print("\n cannot read from individual_property cache...");
            individuals_property_Cache = new HashMap<>();
        }
//        File indiv_cache_file = new File("Cache/Individual_Properties.dat");
//        if(indiv_cache_file.exists()) {
//            try {
//                ois = new ObjectInputStream(new FileInputStream("Cache/Individual_Properties.dat"));
//                individuals_property_Cache = (HashMap<String, HashSet<CruiseProperty>>) ois.readObject();
//                ois.close();
//            } catch (Exception e) {
//                individuals_property_Cache = new HashMap<>();
//            }
//        }
    }
    private static void writeCache_SameLabel(HashMap<String, ArrayList<String>> matches) throws Exception {
        try {
            System.out.print("\n writing in individual cache...");
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("Cache/SameLabelCache.dat"));
            oos.writeObject(matches);
            oos.close();
        } catch(Exception e){
            System.out.print("\n cannot write in same label cache...");
        }
    }
    private static void readCache_SameLabel() {

        ObjectInputStream ois = null;
        same_label_Cache = new HashMap<>();
        try {
            System.out.print("\n reading from individual_property cache...");
            ois = new ObjectInputStream(new FileInputStream("Cache/SameLabelCache.dat"));
            same_label_Cache = (HashMap<String, ArrayList<String>>) ois.readObject();
            ois.close();
        } catch (Exception e) {
            System.out.print("\n cannot read from individual_property cache...");
            same_label_Cache = new HashMap<>();
        }
    }
    private static void writeCache_SamePropertyName( ArrayList<ArrayList<String>> matches) throws Exception {
        try {
            System.out.print("\n writing in individual cache...");
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("Cache/SamePropertyNameCache.dat"));
            oos.writeObject(matches);
            oos.close();
        } catch(Exception e){
            System.out.print("\n cannot write in same PropertyName cache...");
        }
    }
    private static void writeCache_SamePropertyValue( ArrayList<ArrayList<String>>  matches) throws Exception {
        try {
            System.out.print("\n writing in individual cache...");
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("Cache/SamePropertyValueCache.dat"));
            oos.writeObject(matches);
            oos.close();
        } catch(Exception e){
            System.out.print("\n cannot write in same PropertyValue cache...");
        }
    }
    private static void writeCache_SamePropertyName_Value( ArrayList<ArrayList<String>> matches) throws Exception {
        try {
            System.out.print("\n writing in individual cache...");
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("Cache/SamePropertyName_ValueCache.dat"));
            oos.writeObject(matches);
            oos.close();
        } catch(Exception e){
            System.out.print("\n cannot write in same PropertyName cache...");
        }
    }
    private static void readCache_SamePropertyName_Value() {

        DataInputStream ois = null;
        ArrayList<ArrayList<String>> test =  new ArrayList<>();

        try {

            System.out.print("\n reading from individual cache...");
            ois = new DataInputStream(new FileInputStream("Cache/SamePropertyName_ValueCache.dat"));

            for (int i = 0; i < 100; i++) {
                BufferedReader d
                        = new BufferedReader(new InputStreamReader(ois));
//                test = (ArrayList<ArrayList<String>>) ois.readLine();
                System.out.println(  "bbbb  " +d.readLine());
            }
            ois.close();
        } catch (Exception e) {
            System.out.print("\n cannot read from individual cache...");
            test = new ArrayList<ArrayList<String>>();
        }

//            for (int i = 0 ; i < 10 ; i++){
//            System.out.println(test.get(i).toString());
//            }
    }
    private static void writeCache_best_uniqCov( HashMap<String, HashSet<CruiseProperty>> indiv) throws Exception {
        try {
            System.out.print("\n writing in individual cache...");
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("Cache/best_Cov_Uni_indv.dat"));
            oos.writeObject(indiv);
            oos.close();
        } catch(Exception e){
            System.out.print("\n cannot write in same PropertyName cache...");
        }
    }

    public static void main(String[] args) throws Exception {

        long startTime = System.currentTimeMillis();

        // entity type that we want to investigate
        String type = "http://schema.geolink.org/1.0/base/main#Cruise";

        //-------first run this to get all uris
        // querying for the uri of all individuals of this type
        getIndividuals(type);
        System.out.println("\n URI size:  " + individualsCache.size());


/*      //-------second run this to get all properties
        // querying for all individuals and corresponding set of properties, property value, value label, ...
        getIndivisualProperties();
        // cleaning the individual_property data structure before
        individuals_property_Cache = new HashMap<>();
        readCache_Individuals_Property();
*/

/*
        // printing the big data structure of entities and properties
//        System.out.print("\n test of property set --------------------------\n");
//        for(String st: individuals_property_Cache.keySet()){
//            System.out.print("\n individual"+st + "----------*********************************************************"+ "\n");
//            for(CruiseProperty cc: individuals_property_Cache.get(st)){
//                System.out.print("\n"+"CruiseProperty--------------------"+ "\n");
//                System.out.print(cc.getName());
//                System.out.print("\n         value         "+cc.getValue());
//                System.out.print("\n            datatype    " + cc.datatype);
//            }
//        }*/

        // all individuals

/*      //-------third run this to get all proposed uri with the same label:
        // proposing the individuals with same label
        readCache_Individuals_Property(); //first reading the cache and save it here for use
        HashMap<String, ArrayList<String>> matched_On_Label =  new HashMap<>();
        matched_On_Label = same_label_proposer(individuals_property_Cache);
*/

/*      //-------forth run this to get all proposed pair of uris with at least one property name in common
        // proposing the individuals with at least one property in common
        readCache_Individuals_Property();
        ArrayList<ArrayList<String>> matched_on_propertyName = new ArrayList<>();
        matched_on_propertyName = same_peopertyName_proposer(individuals_property_Cache);
*/

/*      //-------fifth run this to get all proposed pair of uri with at least one property name and Value in common
        readCache_Individuals_Property();
        ArrayList<ArrayList<String>> matched_on_propertyName_value = new ArrayList<>();
        matched_on_propertyName_value = same_property_Name_Value_proposer(individuals_property_Cache);
*/

//        readCache_SamePropertyName_Value();

/*      //-------sixth proposing the individuals with at least one property value in common
        readCache_Individuals_Property();
        ArrayList<ArrayList<String>> matched_on_propertyValue = new ArrayList<>();
        matched_on_propertyValue = same_peopertyValue_proposer(individuals_property_Cache);
*/


/*       //-------seventh proposing the individuals based on selected properties (coverage - uniqueness)
        readCache_Individuals_Property();
        HashMap<String, HashSet<CruiseProperty>> all_individuals_with_selected_properties = new HashMap<>();
        all_individuals_with_selected_properties = best_uniqCov_property_set_proposer(individuals_property_Cache, type, 0.9, 10, false);

*/
        System.out.println("Finished proposing the pairs!_______________________");

    }
}
