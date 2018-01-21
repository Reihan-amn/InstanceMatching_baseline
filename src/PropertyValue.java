public class PropertyValue implements java.io.Serializable {

    String uri;
    String label;

    public PropertyValue(String uri){

        this.uri = uri;
        this.label = uri;
        //as default the values that don't have label, uri is their label too. Then we can change the uri to actual label later using setValue method
    }

    public String toString(){
        return uri;
    }
    public void setURI(String uri){
        this.uri = uri;
    }
    public void setLable(String label){
        //only when a value has property rdfs:Label, this method will be called
        this.label = label;
    }
    public String getURI(){
        return this.uri;
    }
    public String getLabel(){
        return this.label;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((uri == null) ? 0 : uri.hashCode());
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
        PropertyValue other = (PropertyValue) obj;
        if (uri == null) {
            if (other.uri != null)
                return false;
        } else if (!uri.equals(other.uri))
            return false;
        return true;
    }

}
