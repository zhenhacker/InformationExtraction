package nlptool;

public class Edge {
    private int idx = -1;
    private String edgetype = "";
    
    public int GetNodeId()
    {
    	return this.idx;
    }
    public void SetNodeId(int id)
    {
    	this.idx = id;
    }
    public String GetEdgeType()
    {
    	return this.edgetype;
    }
    public void SetEdgeType(String et)
    {
    	this.edgetype = et;
    }
}