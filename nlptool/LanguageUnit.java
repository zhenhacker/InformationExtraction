package nlptool;

import java.util.ArrayList;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;

public class LanguageUnit {
	private int idx, occupytks;
    private String word, pos, ner, after;
    public Edge ancestors = new Edge();
    public ArrayList<Edge> descendors = new ArrayList<Edge>();
    
    public LanguageUnit()
    {
        idx = 0;
        occupytks = 1;
        word = "S";
        pos = "";
        ner = "";
        after = "";
    }
    
    public LanguageUnit(int idx, String word, String pos, String ner, String after)
    {
    	this.idx = idx;
    	this.occupytks = 1;
    	this.word = word;
    	this.pos = pos;
    	this.ner = ner;
    	this.after = after;
    }
    
    public LanguageUnit(CoreLabel lb)
    {
    	this.idx = lb.index();
    	this.occupytks = 1;
    	this.word = lb.word().toString();
    	this.pos = lb.tag().toString();
    	this.ner = lb.get(CoreAnnotations.AnswerAnnotation.class).toString();
    	this.after = lb.after();
    }
    
    public void SetIndex(int idx)
    {
    	this.idx = idx;
    }
    
    public int GetIndex()
    {
    	return this.idx;
    }
    
    public void SetOccupyTks(int ot)
    {
    	this.occupytks = ot;
    }
    
    public int GetOccupyTks()
    {
        return this.occupytks;	
    }
    
    public void SetWord(String word)
    {
    	this.word = word;
    }
    
    public String GetWord()
    {
        return this.word;
    }
    
    public void SetPos(String pos)
    {
    	this.pos = pos;
    }
    
    public String GetPos()
    {
    	return this.pos;
    }
    
    public String GetNer()
    {
    	return this.ner;
    }
    
    public void SetAfter(String after)
    {
    	this.after = after;
    }
    
    public String GetAfter()
    {
    	return this.after;
    }
}