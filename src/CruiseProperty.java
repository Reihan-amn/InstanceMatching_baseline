
import java.io.Serializable;
import java.util.HashSet;


public class CruiseProperty implements Serializable {

    private static final long serialVersionUID = 4023479611621851121L;

    public String label;
    public String name;
    public String datatype;
    public HashSet<PropertyValue> value = new HashSet<>();
    public boolean incoming;
    public int used;
    public int unique;

    public CruiseProperty(String name) {
        this.name = name;
        this.value = null;
    }

    public CruiseProperty(String name, String datatype) {
        this.name = name;
        this.datatype = datatype;
        incoming = false;
    }

    public String toString() {
        return name;
    }

    public String getName(){
        return this.name;
    }

    /*each value has a uri and label
    here we */
    public void setValue(String value, String valLabel) { // it takes a HashSet of string and then for each creates a class of PropertyValue

        PropertyValue pv = new PropertyValue(value);
        pv.setLable(valLabel);
        this.value = new HashSet<PropertyValue>();
        this.value.add(pv);

    }
    public HashSet<PropertyValue> getValue(){
        return this.value;
    }
    public void addValue(String Value, String valeLabel){
        PropertyValue pv = new PropertyValue(Value);
        pv.setLable(valeLabel);
        this.value.add(pv);
    }


    public void setLabel(String Label){
        this.label = Label;
    }
    public String getLabel(){
        return label;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CruiseProperty other = (CruiseProperty) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }
}
