package nlptool;

import java.util.*;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.process.*;
import edu.stanford.nlp.tagger.maxent.*;
import edu.stanford.nlp.ie.*;
import edu.stanford.nlp.ie.crf.*;
import edu.stanford.nlp.parser.lexparser.*;
import edu.stanford.nlp.trees.*;

public class SbSProcessor {
	private static int hashbound = 16777216;
	
	public WordToSentenceProcessor<CoreLabel> sentencesegmentor = null;
	public MaxentTagger postagger = null;
	public AbstractSequenceClassifier<CoreLabel> nertagger = null;
	public LexicalizedParser lp = null;
	public CoreLabelTokenFactory tf = null;
	
	public SbSProcessor(String path0, String path1, String path2)
	{
		sentencesegmentor = new WordToSentenceProcessor<CoreLabel>();
		postagger = new MaxentTagger(path0);
		nertagger = CRFClassifier.getClassifierNoExceptions(path1);
		lp = LexicalizedParser.loadModel(path2,"-outputFormat","wordsAndTags,penn,typedDependencies","-outputFormatOptions","basicDependencies");
		this.tf = new CoreLabelTokenFactory();
	}
	
    public ArrayList<CoreLabel> Tokenization(String article)
    {
    	ArrayList<CoreLabel> tokens = new ArrayList<CoreLabel>();
        PTBTokenizer<CoreLabel> ptbt = new PTBTokenizer<CoreLabel>(new java.io.StringReader(article),
        		tf, "invertible=true,normalizeParentheses=true");
        
        for (CoreLabel label; ptbt.hasNext();)
        {
        	label = ptbt.next();
            tokens.add(label);	
        }
        
        return tokens;
    }
    
    public ArrayList<CoreLabel> TokenizationWithOriginalForm(String article)
    {
    	ArrayList<CoreLabel> tokens = new ArrayList<CoreLabel>();
        PTBTokenizer ptbt = new PTBTokenizer(new java.io.StringReader(article),
        		new CoreLabelTokenFactory(), "invertible=true,normalizeParentheses=false");
        
        for (CoreLabel label; ptbt.hasNext();)
        {
        	label = (CoreLabel)ptbt.next();
            tokens.add(label);	
        }
        
        return tokens;
    }
    
    public List<List<CoreLabel>> SentenceSegmentation(List<CoreLabel> tokens)
    {
    	ArrayList<ArrayList<CoreLabel>> result = new ArrayList<ArrayList<CoreLabel>>();
    	//WordToSentenceProcessor<CoreLabel> ssor = new WordToSentenceProcessor<CoreLabel>();
    	return sentencesegmentor.process(tokens);
    }

    public String BuildOriginalSentence(List<CoreLabel> ts)
    {
        StringBuilder result = new StringBuilder();
        
        for (CoreLabel tk : ts)
        {
            result.append(tk.word());
            result.append(tk.after());
        }
        
        return result.toString();
    }
    
    public void POSTag(List<CoreLabel> ts)
    {
    	if (ts.size()==0) return;
    	postagger.tagCoreLabels(ts);
    }
    
    public ArrayList<CoreLabel> NERTag(List<CoreLabel> ts)
    {
    	ArrayList<CoreLabel> result = new ArrayList<CoreLabel>(); 
    	
    	for (CoreLabel tk : nertagger.classifySentence(ts))
    	{
    		result.add(tk);
    	}
    	
    	return result;
    }
    
    public Tree DependencyParse(List<CoreLabel> ts)
    {
        //return lp.apply(ts);
        return lp.parse(ts);
    }
 
    public Collection<TypedDependency> Tree2Dependencies(Tree t)
    {
    	TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    	GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
    	GrammaticalStructure gs = gsf.newGrammaticalStructure(t);
    	return gs.typedDependencies();    //basic, ensure 1) tree; 2) connected
    }

    public ArrayList<LanguageUnit> Syn(List<CoreLabel> tks, Collection<TypedDependency> dps)
    {
    	ArrayList<LanguageUnit> result = new ArrayList<LanguageUnit>();
    	LanguageUnit Root = new LanguageUnit();
    	Root.ancestors.SetNodeId(-1);    //for root, no father (-1)
    	result.add(Root);
    	for (CoreLabel tk : tks)
    	{
    		LanguageUnit syntk = new LanguageUnit(tk);
    		syntk.SetIndex(result.size());
    		result.add(syntk);
    	}
    	for (TypedDependency dp : dps)
    	{
    		int uid = dp.gov().label().index();
    		int vid = dp.dep().label().index();
    		//if (uid < 0 || vid < 0 ) continue;
    		String et = dp.reln().getShortName();
    		
    		Edge pe = new Edge();
    		pe.SetNodeId(vid);
    		pe.SetEdgeType(et);
    		result.get(uid).descendors.add(pe);
    		
    		result.get(vid).ancestors.SetEdgeType(et);
    		result.get(vid).ancestors.SetNodeId(uid);
    	}
    	
    	return result;
    }
    
    private boolean IsContiguousParsed(int x, int y, List<LanguageUnit> t)
    {
    	if (t.get(x).ancestors.GetNodeId()==y ||
    		t.get(y).ancestors.GetNodeId()==x)
    		return true;
    	
    	if (t.get(x).ancestors.GetNodeId()==t.get(y).ancestors.GetNodeId() &&
    		t.get(x).ancestors.GetEdgeType().equals(t.get(y).ancestors.GetEdgeType()))
    		return true;
    	
    	return false;
    }
    
    private void AbsorbNode(int x, int y, List<LanguageUnit> t)
    {
    	LanguageUnit xlu = t.get(x);
    	LanguageUnit ylu = t.get(y);
    	
    	if (xlu.ancestors.GetNodeId()==y)
    	{
    	    xlu.ancestors.SetNodeId(ylu.ancestors.GetNodeId());
    	    if ( ylu.ancestors.GetNodeId()>=0) 
    	    {
	    	    for (Edge e : t.get(ylu.ancestors.GetNodeId()).descendors)
	    	    {
	    	    	if (e.GetNodeId()==y)
	    	    	{
	    	    		e.SetNodeId(x);
	    	    		break;
	    	    	}
	    	    }
    	    }
    	    for(Edge e : ylu.descendors)
    	    {
    	    	if (e.GetNodeId()==x) continue;
    	    	t.get(e.GetNodeId()).ancestors.SetNodeId(x);
    	        xlu.descendors.add(e);
    	    }
    	}
    	else if (ylu.ancestors.GetNodeId()==x)
    	{
    		for (int i = 0; i < xlu.descendors.size(); i++)
    		{
    			if (xlu.descendors.get(i).GetNodeId()==y)
    			{
    				xlu.descendors.remove(i);
    				break;
    			}
    		}
    		for(Edge e : ylu.descendors)
    		{
    			t.get(e.GetNodeId()).ancestors.SetNodeId(x);
    			xlu.descendors.add(e);
    		}
    	}
    	else 
    	{
    		if ( xlu.ancestors.GetNodeId()>=0) 
    		{
	    		LanguageUnit zlu = t.get(xlu.ancestors.GetNodeId());
	    		for (int i = 0; i < zlu.descendors.size();i++)
	    	    {
	    	    	if (zlu.descendors.get(i).GetNodeId()==y) 
	    	    	{
	    	    		//System.out.print("delete "+t.get(zlu.descendors.get(i).GetNodeId()).GetWord());
	    	    		zlu.descendors.remove(i);
	    	    	    break;
	    	    	}
	    	    }
    		}
    	    for (Edge e : ylu.descendors)
    	    {
    	    	xlu.descendors.add(e);
    	    	t.get(e.GetNodeId()).ancestors.SetNodeId(x);
    	    }
    	}
    	
    	xlu.SetOccupyTks(xlu.GetOccupyTks()+1);
    	xlu.SetWord(xlu.GetWord()+xlu.GetAfter()+ylu.GetWord());
    	xlu.SetAfter(ylu.GetAfter());
    	xlu.SetPos(ylu.GetPos());
    	ylu.SetIndex(x);    //set as dummy lu
    }
    
    public void Chunking(List<LanguageUnit> orig)
    {
    	for (int i = 0; i < orig.size(); i = i + orig.get(i).GetOccupyTks())
    	{
    	    String w = orig.get(i).GetNer();
    	    if (w.equals("PERSON") ||
    	    	w.equals("LOCATION") ||
    	    	w.equals("ORGANIZATION") ||
    	    	w.equals("MISC")) 
    	    {
    	        for (int j = i+1; j<orig.size();j++)
    	        {
    	            if (!orig.get(j).GetNer().equals(w) ||
    	            	!IsContiguousParsed(i,j,orig)) 
    	            {
    	            	break;
    	            }
    	            AbsorbNode(i,j,orig);
    	        }
    	    }
    	}
    }
    
    public int MyHashString(String tf)
    {
    	int hv = 19;
    	
    	for (int i = 0; i < tf.length(); i++)
    	{
    		hv = (11*hv + tf.charAt(i)) % hashbound;
    	}
    	
    	return hv;
    }
    
    private ArrayList<Edge> GetAncestors(int x, List<LanguageUnit> lbs)
    {
    	ArrayList<Edge> result = new ArrayList<Edge>();
    	
    	for ( LanguageUnit it = lbs.get(x); it.ancestors.GetNodeId() != -1; it = lbs.get(it.ancestors.GetNodeId()))
    	{
    		result.add(it.ancestors);
    	}
    	
    	return result;
    }
    
    public String Tags2Str(ArrayList<CoreLabel> tks)
    {
    	StringBuilder result = new StringBuilder();
        for(CoreLabel cl : tks)
        {
        	
        	result.append(cl.toString());
        	result.append(";");
        }
        
        return result.toString();
    }
    
    
    
    public ArrayList<LanguageUnit> ParseSentence(String s)
    {
    	ArrayList<CoreLabel> tks = Tokenization(s);
    	if ( tks.size()==0) return new ArrayList<LanguageUnit>();
    	
        POSTag(tks);
        tks = NERTag(tks);
        Tree dt = DependencyParse(tks);
        Collection<TypedDependency> dps = Tree2Dependencies(dt);
        ArrayList<LanguageUnit> lus = Syn(tks,dps);
        Chunking(lus);
        
        return lus;
    }
    
    public ArrayList<String> GenerateSyntacticX(int x, int y, List<LanguageUnit> lbs)
    {
    	StringBuilder result = new StringBuilder();
    	StringBuilder revresult = new StringBuilder();
    	
    	ArrayList<Edge> xancs = GetAncestors(x,lbs);
    	ArrayList<Edge> yancs = GetAncestors(y,lbs);
    	
    	//determine lca
    	int xidx = xancs.size()-1, yidx = yancs.size()-1, lca = -1;
    	while (xidx>=0 && yidx>=0)
    	{
    	    if (xancs.get(xidx).GetNodeId()!=yancs.get(yidx).GetNodeId()) break;
    	    xidx--;
    	    yidx--;
    	}
    	if ( xidx < 0 && yidx < 0 ) //x and y are siblings, lca to y begin with yidx+1
    	{
    	    lca = xancs.get(0).GetNodeId();
    	    yidx++;
    	}
    	else if (xidx < 0 && yidx >= 0)
    	{
    		if ( yancs.get(yidx).GetNodeId() == x) //x is lca, lca to y begin with yidx
    		{
    			lca = x;
    		}
    		else //z is lca, lca to y begin with yidx+1
    		{
    			lca = xancs.get(0).GetNodeId();
    			yidx++;
    		}
    	}
    	else if (xidx >=0 && yidx < 0)
    	{
    		if ( xancs.get(xidx).GetNodeId() == y ) //y is lca, lca to y begin with yidx (-1)
    		{
    			lca = y;
    		}
    		else //z is lca, lca to y begin with yidx+1
    		{
    			lca = yancs.get(0).GetNodeId();
    			yidx++;
    		}
    	}
    	else //z is lca, lca to y begin with yidx+1
    	{
    		lca = xancs.get(xidx+1).GetNodeId();
    		yidx++;
    	}
    	
    	//build middle syntactic feature
    	StringBuilder mdstr = new StringBuilder();
    	if (lca != x)
    	{
    	    for ( int i = 0; i < xancs.size(); i++)
    	    {
    		    mdstr.append("u");
    		    mdstr.append(xancs.get(i).GetEdgeType());
    		    if ( xancs.get(i).GetNodeId() != y)
    		    {
    		        mdstr.append(lbs.get(xancs.get(i).GetNodeId()).GetWord());
    		    }
    		    if ( xancs.get(i).GetNodeId() == lca ) break;
    	    }
    	}
    	for ( int i = yidx; i >= 0; i--)
    	{
    	    if ( lca != yancs.get(i).GetNodeId()) 
    		{
    			mdstr.append(lbs.get(yancs.get(i).GetNodeId()).GetWord());
    		}
    		mdstr.append("d");
    		mdstr.append(yancs.get(i).GetEdgeType());
    	}
    	
    	//construct result
    	LanguageUnit xlu = lbs.get(x), ylu = lbs.get(y);
    	result.append(xlu.GetWord());
    	result.append("\t");
    	result.append(xlu.GetNer());
    	result.append("\t");
    	result.append(ylu.GetWord());
    	result.append("\t");
    	result.append(ylu.GetNer());
    	result.append("\t");
    	revresult.append(ylu.GetWord());
    	revresult.append("\t");
    	revresult.append(ylu.GetNer());
    	revresult.append("\t");
    	revresult.append(xlu.GetWord());
    	revresult.append("\t");
    	revresult.append(xlu.GetNer());
    	revresult.append("\t");
    	
    	//build feature vector
    	//collect window
    	if ( lca == x )
    	{
    		String xwd = "";
    		if ( xlu.ancestors.GetNodeId() != -1)
    		{
    			xwd = lbs.get(xlu.ancestors.GetNodeId()).GetWord() + "d" + xlu.ancestors.GetEdgeType();
    		}
    	    for ( Edge son : ylu.descendors)
    	    {
    	    	String ywd = "d" + son.GetEdgeType()+lbs.get(son.GetNodeId()).GetWord();
    	    	result.append(MyHashString(xwd+xlu.GetNer()+mdstr+ylu.GetNer()+ywd));
    	    	result.append(",");
    	    	revresult.append(MyHashString("R"+xwd+xlu.GetNer()+mdstr+ylu.GetNer()+ywd));
    	    	revresult.append(",");
    	    }
    	}
    	else if (lca == y)
    	{
    		String ywd = "";
    		if ( ylu.ancestors.GetNodeId() != -1)
    		{
    			ywd = "u" + ylu.ancestors.GetEdgeType()+lbs.get(ylu.ancestors.GetNodeId()).GetWord();
    		}
    	    for ( Edge son : xlu.descendors)
    	    {
    	    	String xwd = lbs.get(son.GetNodeId()).GetWord() + "u" + son.GetEdgeType();
    	    	result.append(MyHashString(xwd+xlu.GetNer()+mdstr+ylu.GetNer()+ywd));
    	    	result.append(",");
    	    	revresult.append(MyHashString("R"+xwd+xlu.GetNer()+mdstr+ylu.GetNer()+ywd));
    	    	revresult.append(",");
    	    }
    	}
    	else 
    	{
    	    	for(Edge xson : xlu.descendors)
    	    	{
    	    		for(Edge yson : ylu.descendors)
    	    		{
    	    			String xwd = lbs.get(xson.GetNodeId()).GetWord()+"u"+xson.GetEdgeType();
    	    			String ywd = "d" + yson.GetEdgeType() + lbs.get(yson.GetNodeId()).GetWord();
    	    			result.append(MyHashString(xwd+xlu.GetNer()+mdstr+ylu.GetNer()+ywd));
    	    			result.append(",");
    	    			revresult.append(MyHashString("R"+xwd+xlu.GetNer()+mdstr+ylu.GetNer()+ywd));
    	    			revresult.append(",");
    	    		}
    	    	}
    	}
    	
    	result.append(MyHashString(xlu.GetNer()+mdstr.toString()+ylu.GetNer()));
    	result.append(",");
    	revresult.append(MyHashString("R"+xlu.GetNer()+mdstr.toString()+ylu.GetNer()));
    	revresult.append(",");
    	
    	ArrayList<String> vh = new ArrayList<String>();
    	vh.add(result.toString());
    	vh.add(revresult.toString());
    	return vh;
    }
    
    public ArrayList<String> GenerateLexicalX(int x,int y, List<LanguageUnit> rawinput)
    {
    	StringBuilder result = new StringBuilder();
    	StringBuilder resultrev = new StringBuilder();
    	
    	LanguageUnit xlu = rawinput.get(x);
    	LanguageUnit ylu = rawinput.get(y);
    	
    	result.append(xlu.GetWord());
    	result.append("\t");
    	result.append(xlu.GetNer());
    	result.append("\t");
    	result.append(ylu.GetWord());
    	result.append("\t");
    	result.append(ylu.GetNer());
    	result.append("\t");
    	
    	resultrev.append(ylu.GetWord());
    	resultrev.append("\t");
    	resultrev.append(ylu.GetNer());
    	resultrev.append("\t");
    	resultrev.append(xlu.GetWord());
    	resultrev.append("\t");
    	resultrev.append(xlu.GetNer());
    	resultrev.append("\t");
    	
    	StringBuilder LexicalMiddle = new StringBuilder();
    	for (int i = x + xlu.GetOccupyTks(); i < y && i < rawinput.size(); i = i + rawinput.get(i).GetOccupyTks())
    	{
    		LexicalMiddle.append(rawinput.get(i).GetWord());
    	    LexicalMiddle.append(rawinput.get(i).GetPos());
    	}
    	result.append(MyHashString(xlu.GetNer()+LexicalMiddle.toString()+ylu.GetNer()));
    	result.append(",");
    	resultrev.append(MyHashString("R"+xlu.GetNer()+LexicalMiddle.toString()+ylu.GetNer()));
    	resultrev.append(",");
    	
    	int l = x-1;
    	String lw = "";
    	while (l>0)
    	{
    		if (rawinput.get(l).GetIndex()==l)
    		{
    		    lw = rawinput.get(l).GetWord();
    		    break;
    		}
    		l--;
    	}
    	if (l==0)
    	{
    		lw = "#PAD#";
    	}
    	String rw = "#PAD#";
    	int r = y + ylu.GetOccupyTks();
    	if (r < rawinput.size())
    	{
    		rw = rawinput.get(r).GetWord();
    	}
    	result.append(MyHashString(lw+xlu.GetNer()+LexicalMiddle.toString()+ylu.GetNer()+rw));
    	result.append(",");
    	resultrev.append(MyHashString("R"+lw+xlu.GetNer()+LexicalMiddle.toString()+ylu.GetNer()+rw));
    	resultrev.append(",");
    	
    	l--;
    	while (l>0)
    	{
    		if (rawinput.get(l).GetIndex()==l)
    		{
    			lw = rawinput.get(l).GetWord()+lw;
    		    break;	
    		}
    		l--;
    	}
    	if ( l <= 0 )
    	{
    		lw = "#PAD##PAD#";
    	}
    	if ( r < rawinput.size()) 
    	{
    	    r = r + rawinput.get(r).GetOccupyTks();
    	}
    	if ( r < rawinput.size())
    	{
    		rw = rw + rawinput.get(r).GetWord();
    	}
    	else 
    	{
    	    rw = rw + "#PAD#";
    	}
    	result.append(MyHashString(lw+xlu.GetNer()+LexicalMiddle.toString()+ylu.GetNer()+rw));
    	result.append(",");
    	resultrev.append(MyHashString("R"+lw+xlu.GetNer()+LexicalMiddle.toString()+ylu.GetNer()+rw));
    	resultrev.append(",");
    	
    	ArrayList<String> vh = new ArrayList<String>();
    	vh.add(result.toString());
    	vh.add(resultrev.toString());
    	return vh;
    }
    
    public ArrayList<String> Observe(List<LanguageUnit> rawinput)
    {
    	ArrayList<String> result = new ArrayList<String>();
    	ArrayList<Integer> nes = new ArrayList<Integer>();
    	
    	for (int i = 0; i < rawinput.size(); i = i+rawinput.get(i).GetOccupyTks())
    	{
    		String curner = rawinput.get(i).GetNer();
    		if (curner.equals("PERSON") ||
    			curner.equals("LOCATION") ||
    			curner.equals("ORGANIZATION") ||
    			curner.equals("MISC"))
    		{
    		    nes.add(i);
    		}
    	}
    	
        for (int i = 0; i < nes.size(); i++)
        {
        	for (int j = i+1; j < nes.size(); j++)
        	{
        		result.addAll(GenerateLexicalX(nes.get(i),nes.get(j),rawinput));
        	}
        }
        
    	return result;
    }
    
    public ArrayList<String> Observepro(List<LanguageUnit> rawinput)
    {
    	ArrayList<String> result = new ArrayList<String>();
    	ArrayList<Integer> nes = new ArrayList<Integer>();
    	
    	for (int i = 0; i < rawinput.size(); i = i+rawinput.get(i).GetOccupyTks())
    	{
    		String curner = rawinput.get(i).GetNer();

    		if (curner.equals("PERSON") ||
    			curner.equals("LOCATION") ||
    			curner.equals("ORGANIZATION") ||
    			curner.equals("MISC"))
    		{
    		    nes.add(i);
    		}
    	}
    	
        for (int i = 0; i < nes.size(); i++)
        {
        	for (int j = i+1; j < nes.size(); j++)
        	{
        		result.addAll(GenerateSyntacticX(nes.get(i),nes.get(j),rawinput));
        	}
        }
        
    	return result;
    }
        
    public static void main(String[] args)
    {
    	SbSProcessor ob = new SbSProcessor("E:/stanford-postagger-2013-06-20/stanford-postagger-2013-06-20/models/wsj-0-18-bidirectional-nodistsim.tagger", 
    			"E:\\stanford-ner-2013-06-20\\stanford-ner-2013-06-20\\classifiers\\english.all.4class.distsim.crf.ser", 
    			"E:/stanford-parser-full-2013-06-20/stanford-parser-full-2013-06-20/stanford-parser-3.2.0-models/edu/stanford/nlp/models/lexparser/englishPCFG.ser/englishPCFG.ser");
    
        String s = "Barack Obama, the president of USA gave a talk for students of Machine Learning Group of MSRA about the application of machine learning in the Big Data era.";
        for (String row : ob.Observe(ob.ParseSentence(s)))
        {
        	System.out.print(row+"\n");
        }
        for (String row : ob.Observepro(ob.ParseSentence(s)))
        {
        	System.out.print(row+"\n");
        }
        System.out.print("completed!");
    }
}
