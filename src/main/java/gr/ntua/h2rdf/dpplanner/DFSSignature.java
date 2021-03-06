package gr.ntua.h2rdf.dpplanner;

import gr.ntua.h2rdf.LoadTriples.ByteTriple;
import gr.ntua.h2rdf.indexScans.BGP;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.PriorityQueue;

import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.sparql.algebra.OptimizeOpVisitorDPCaching;
import com.hp.hpl.jena.sparql.core.Var;

public class DFSSignature {

	private OptimizeOpVisitorDPCaching visitor;
	private BitSet edges;
	private CachingExecutor cachingExecutor;
	public HashMap<Integer,VarNode> varNodes;
	public TreeMap<VarNode, PriorityQueue<TriplePatternEdge>> graph;
	
	private TreeMap<Integer, BitSet> varGraph ;
	
	public DFSSignature(BitSet edges, OptimizeOpVisitorDPCaching visitor,
			CachingExecutor cachingExecutor) {
		this.visitor=visitor;
		this.edges=edges;
		this.cachingExecutor=cachingExecutor;
		varGraph= new TreeMap<Integer, BitSet>();
		for(Integer i :visitor.varGraph.keySet()){
			BitSet val = visitor.varGraph.get(i);
			BitSet valnew = new BitSet();
			valnew.or(val);
			valnew.and(edges);
			if(!valnew.isEmpty()){
				varGraph.put(i, valnew);
			}
		}
		System.out.println(varGraph);
		
		varNodes = new HashMap<Integer, VarNode>();
		TreeMap<String,Integer> nodeSig = new TreeMap<String,Integer>();
		for(Integer i :varGraph.keySet()){
			VarNode vn = new VarNode(varGraph, visitor, i);
			Integer sim = nodeSig.get(vn.signature);
			if(sim==null)
				nodeSig.put(vn.signature, 1);
			else
				sim++;
			varNodes.put(i, vn);
		}
		for(VarNode v : varNodes.values()){
			v.setSimilar(nodeSig.get(v.getSignature()));
		}
		System.out.println(varNodes);

		graph=new TreeMap<VarNode, PriorityQueue<TriplePatternEdge>>();
		for(Integer i :varGraph.keySet()){
			BitSet val = varGraph.get(i);
			PriorityQueue<TriplePatternEdge> pr =  new PriorityQueue<TriplePatternEdge>();
			for (int j = val.nextSetBit(0); j >= 0; j = val.nextSetBit(j+1)) {
				BGP bgp = visitor.bgpIds.get(j);
				ByteTriple btr = bgp.byteTriples.get(0);

				TriplePatternEdge tp = new TriplePatternEdge(visitor);
				tp.tripleId=j;
				tp.srcPos= bgp.varPos.get(visitor.varIds.get(i).toString());
				if(tp.srcPos.equals("s")){
					tp.edgePos="p";
					if(btr.getP()==0){
						VarNode vnt = varNodes.get(visitor.varRevIds.get(bgp.bgp.getPredicate()));
						tp.edgeId=vnt.getSimilar()+"_"+vnt.getSignature();
						tp.destVars.add(vnt);
					}
					else{
						tp.edgeId=btr.getP()+"";
					}
					tp.destPos="o";
					if(btr.getO()==0){
						VarNode vnt = varNodes.get(visitor.varRevIds.get(bgp.bgp.getObject()));
						tp.destId=vnt.getSimilar()+"_"+vnt.getSignature();
						tp.destVars.add(vnt);
					}
					else{
						tp.destId=btr.getP()+"";
					}
				}
				else if(tp.srcPos.equals("p")){
					tp.edgePos="s";
					if(btr.getS()==0){
						VarNode vnt = varNodes.get(visitor.varRevIds.get(bgp.bgp.getSubject()));
						tp.edgeId=vnt.getSimilar()+"_"+vnt.getSignature();
						tp.destVars.add(vnt);
					}
					else{
						tp.edgeId=btr.getS()+"";
					}
					tp.destPos="o";
					if(btr.getO()==0){
						VarNode vnt = varNodes.get(visitor.varRevIds.get(bgp.bgp.getObject()));
						tp.destId=vnt.getSimilar()+"_"+vnt.getSignature();
						tp.destVars.add(vnt);
					}
					else{
						tp.destId=btr.getP()+"";
					}
				}
				if(tp.srcPos.equals("o")){
					tp.edgePos="p";
					if(btr.getP()==0){
						VarNode vnt = varNodes.get(visitor.varRevIds.get(bgp.bgp.getPredicate()));
						tp.edgeId=vnt.getSimilar()+"_"+vnt.getSignature();
						tp.destVars.add(vnt);
					}
					else{
						tp.edgeId=btr.getP()+"";
					}
					tp.destPos="s";
					if(btr.getS()==0){
						VarNode vnt = varNodes.get(visitor.varRevIds.get(bgp.bgp.getSubject()));
						tp.destId=vnt.getSimilar()+"_"+vnt.getSignature();
						tp.destVars.add(vnt);
					}
					else{
						tp.destId=btr.getS()+"";
					}
				}
				tp.computeSignature();
				pr.add(tp);
			}
			graph.put(varNodes.get(i), pr);
		}
		
		System.out.println(graph);
		System.out.println(graph.keySet().size());
		
	}

	public void cache() {
		List<DFSInstance> l = new ArrayList<DFSInstance>();
		int i=0;
		VarNode root=null;
		for(VarNode n :graph.keySet()){
			if(i==0){
				root=n;
				DFSInstance d = new DFSInstance(graph, visitor, n, l);
				l.add(d);
			}
			else{
				if(n.equalsTo(root)){
					DFSInstance d = new DFSInstance(graph, visitor, n, l);
					l.add(d);
				}
				else{
					break;
				}
			}
			i++;
		}
		
		while(!l.isEmpty()){
			System.out.println(l.remove(0).runDFS());
		}
	}

}
