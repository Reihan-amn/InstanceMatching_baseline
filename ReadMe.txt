This project contains all baseline approaches for instance matching for semantic dataset (RDF structure).
It compairs the Person,  Cruise, and Organization intance in GeoLink project. (http://www.geolink.org/)

The matching is based on a series of criteria and different threshold:

Criteria:
1. same label
2. same property name
3. same property value
4. same property name and value


Threshold:
1. only based on the properties that have enohgt coverage-discriminability
2. propose matching if for pair of the entity, one of criteria is the same:
	Example:  Two entities can e proposed as possile matches if the have only one property name in common >>> problem: they all can e proposed ecause the property:type is common among the pairs
	solutuon: increasing the threshold >> propose them if there are 3 peoperies in common


	