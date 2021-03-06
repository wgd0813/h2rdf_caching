package gr.ntua.h2rdf.dpplanner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.SequenceFile;

import gr.ntua.h2rdf.indexScans.BGP;
import gr.ntua.h2rdf.indexScans.Bindings;
import gr.ntua.h2rdf.indexScans.CentralizedMergeJoinExecutor;
import gr.ntua.h2rdf.indexScans.MapReduceMergeJoinExecutor1;
import gr.ntua.h2rdf.indexScans.ResultBGP;
import gr.ntua.h2rdf.inputFormat2.TableRecordGroupReader;

import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.sparql.algebra.OptimizeOpVisitorDPCaching;
import com.hp.hpl.jena.sparql.core.Var;

public class IndexScan implements DPJoinPlan{
	public Triple triple;
	public BGP bgp;
	private Double cost;
	private OptimizeOpVisitorDPCaching visitor;
	private CachingExecutor cachingExecutor;
	public List<ResultBGP> results;
	
	public IndexScan(Triple triple, BGP bgp,OptimizeOpVisitorDPCaching visitor,
			CachingExecutor cachingExecutor) {
		this.visitor=visitor;
		this.cachingExecutor=cachingExecutor;
		this.triple=triple;
		this.bgp=bgp;
		cost=0.0;
	}
	
	@Override
	public String print() {
		return "scan: "+triple;
	}

	@Override
	public void execute(OptimizeOpVisitorDPCaching visitor,
			CachingExecutor cachingExecutor) throws IOException, InterruptedException {

		Configuration conf = new Configuration();
		FileSystem fs=FileSystem.get(conf);
		Path out =  new Path("output/join_"+visitor.cachingExecutor.id+"_"+visitor.cachingExecutor.tid);
		System.out.println("Scan");
		
		if(fs.exists(out))
			fs.delete(out, true);
		Byte jVar = (byte) 0;
		Var joinVar = visitor.varIds.get(0);
		List<Scan> sc = bgp.getScans("?"+joinVar.getVarName());
		TableRecordGroupReader groupReader =new TableRecordGroupReader(visitor.table);
		for(Scan s : sc){
			s.setCacheBlocks(true); 
			s.setCaching(10000);
			s.setBatch(10000);
			s.setAttribute("stat0", Bytes.toBytes(bgp.getStatistics(joinVar)[0]));
			s.setAttribute("stat1", Bytes.toBytes(bgp.getStatistics(joinVar)[1]));
			groupReader.addScan(s);
			System.out.println(Bytes.toStringBinary(s.getStartRow())+" "+Bytes.toStringBinary(s.getStopRow()));
		}
		SequenceFile.Writer writer = SequenceFile.createWriter(fs, conf, out, Bindings.class, BytesWritable.class);
		int count=0;
		while(groupReader.nextKeyValue()){

			count++;
			//System.out.println(groupReader.getCurrentKey().get(0).map);
			writer.append(groupReader.getCurrentKey().get(0), new BytesWritable(new byte[0]));
		}
		System.out.println("Results: "+count);
		writer.close();
		groupReader.close();
		
		results = new ArrayList<ResultBGP>();
    	Set<Var> vars = new HashSet<Var>();
		vars.addAll(bgp.joinVars);
		
		long sum =0;
		Map<Integer, double[]> newStats = new HashMap<Integer, double[]>();
		results.add(new ResultBGP(vars, out, newStats));
		
		visitor.cachingExecutor.tid++;
		
		
	}

	@Override
	public int compareTo(DPJoinPlan o) {
		return cost.compareTo(o.getCost());
	}
	@Override
	public Double getCost() {
		return cost;
	}

	@Override
	public double[] getStatistics(Integer joinVar) throws IOException {
		/*double[] ret = new double[2];
		ret[0]=10000;
		ret[1]=12;
		return ret;*/
    	long start = System.nanoTime();
    	Var v =visitor.varIds.get(joinVar);
		double[] ret =bgp.getStatistics(v);

    	long time = System.nanoTime()-start;
    	
    	if(time>=1000000){
    		visitor.numStats++;
    	}
    	visitor.statsTime+=time;
		return ret;
	}

	@Override
	public List<ResultBGP> getResults() throws IOException {
		return results;
	}

	@Override
	public void computeCost() throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public List<Integer> getOrdering() {
		// TODO Auto-generated method stub
		return null;
	}

}
