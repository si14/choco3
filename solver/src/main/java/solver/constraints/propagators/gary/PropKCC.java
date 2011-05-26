/**
 *  Copyright (c) 1999-2011, Ecole des Mines de Nantes
 *  All rights reserved.
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 *      * Redistributions of source code must retain the above copyright
 *        notice, this list of conditions and the following disclaimer.
 *      * Redistributions in binary form must reproduce the above copyright
 *        notice, this list of conditions and the following disclaimer in the
 *        documentation and/or other materials provided with the distribution.
 *      * Neither the name of the Ecole des Mines de Nantes nor the
 *        names of its contributors may be used to endorse or promote products
 *        derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND ANY
 *  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY
 *  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package solver.constraints.propagators.gary;

import java.util.BitSet;
import java.util.LinkedList;
import gnu.trove.TIntArrayList;
import choco.kernel.ESat;
import choco.kernel.memory.IEnvironment;
import solver.constraints.Constraint;
import solver.constraints.propagators.GraphPropagator;
import solver.constraints.propagators.Propagator;
import solver.constraints.propagators.PropagatorPriority;
import solver.exception.ContradictionException;
import solver.variables.EventType;
import solver.variables.IntVar;
import solver.variables.graph.GraphVar;
import solver.variables.graph.IActiveNodes;
import solver.variables.graph.INeighbors;
import solver.variables.graph.directedGraph.DirectedGraphVar;
import solver.variables.graph.graphOperations.connectivity.ConnectivityFinder;
import solver.variables.graph.graphOperations.connectivity.ConnectivityObject;
import solver.requests.IRequest;

/**Propagator that ensures that the final graph consists in K Connected Components (CC)
 * 
 * TODO : couplage generalise, incremental?
 * 
 * @author Jean-Guillaume Fages
 */
public class PropKCC<V extends GraphVar> extends GraphPropagator<V>{

	//***********************************************************************************
	// VARIABLES
	//***********************************************************************************

	private GraphVar g;
	private IntVar k;

	//***********************************************************************************
	// CONSTRUCTORS
	//***********************************************************************************

	public PropKCC(V graph, IEnvironment environment, Constraint<V, Propagator<V>> constraint, IntVar k) {
		super((V[]) new GraphVar[]{graph}, environment, constraint, PropagatorPriority.LINEAR, false);
		g = graph;
		this.k = k;
		initDataStructure();
	}

	//***********************************************************************************
	// PROPAGATIONS
	//***********************************************************************************

	@Override
	public void propagate() throws ContradictionException {
		int n = g.getEnvelopGraph().getNbNodes();
		IActiveNodes env = g.getEnvelopGraph().getActiveNodes();
		IActiveNodes ker = g.getKernelGraph().getActiveNodes();
		ConnectivityObject envCcObj = findAll();
		boolean tV;
		int min = 0;
		LinkedList<TIntArrayList> ccWithNoTV = new LinkedList<TIntArrayList>();
		int[] ccof = new int[n];
		int idx=0;
		for(TIntArrayList cc:envCcObj.getConnectedComponents()){
			tV = false;
			for(int i=0; i<cc.size(); i++){
				ccof[cc.get(i)] = idx;
				if(ker.isActive(cc.get(i))){
					tV = true;
				}
			}
			if(tV){
				min++;
			}else{
				ccWithNoTV.add(cc);
			}idx++;
		}
		int max = ConnectivityFinder.findCCOf(g.getKernelGraph()).size() + g.getEnvelopOrder() - g.getKernelOrder();
		// TODO couplage generalise
		
		// PRUNING
		// --- Bounds
		k.updateLowerBound(min, this);
		k.updateUpperBound(max, this);
		if(k.instantiated()){
			// --- Max
			if(k.getValue()==max){
				INeighbors nei;
				for(int i = env.nextValue(0); i>=0; i = env.nextValue(i+1)){
					nei = g.getEnvelopGraph().getNeighborsOf(i);
					for(int j = nei.getFirstElement(); j>=0; j = nei.getNextElement()){
						if(ccof[i]!=ccof[j] && i!=j){
							g.removeArc(i, j, this);
						}
					}
				}
			}
			// --- Min
			if(k.getValue()==min){
				// --- remove nodes of CC with no T-Vertex
				for(TIntArrayList cc:ccWithNoTV){
					for(int i=0; i<cc.size(); i++){
						g.removeNode(cc.get(i), this);
					}
				}
				// --- add articulation points that split at least two TVertices to the kernel
				BitSet ap = envCcObj.getArticulationPoints();
				for(int i=ap.nextSetBit(0); i>=0; i = ap.nextSetBit(i+1)){
					g.enforceNode(i, this);
				}
				// --- add isthmus that split at least two TVertices to the kernel
				TIntArrayList isthmus = envCcObj.getIsthmus();
				int from,to;
				for(int i=0; i<isthmus.size(); i++){
					from = isthmus.get(i)/n-1;
					to   = isthmus.get(i)%n;
					if(g instanceof DirectedGraphVar){
						DirectedGraphVar dig = (DirectedGraphVar) g;
						if (dig.getEnvelopGraph().arcExists(from, to) && !dig.getEnvelopGraph().arcExists(to, from) ){
							g.enforceArc(from, to, this);
						}else {
							g.enforceArc(to, from, this);
						}
					}else{
						g.enforceArc(from, to, this);
					}
				}
			}
		}
	}

	@Override
	public void propagateOnRequest(IRequest<V> request, int idxVarInProp, int mask) throws ContradictionException {
		int n = g.getEnvelopGraph().getNbNodes();
		IActiveNodes env = g.getEnvelopGraph().getActiveNodes();
		IActiveNodes ker = g.getKernelGraph().getActiveNodes();
		ConnectivityObject envCcObj = findAll();
		boolean tV;
		int min = 0;
		LinkedList<TIntArrayList> ccWithNoTV = new LinkedList<TIntArrayList>();
		int[] ccof = new int[n];
		int idx=0;
		for(TIntArrayList cc:envCcObj.getConnectedComponents()){
			tV = false;
			for(int i=0; i<cc.size(); i++){
				ccof[cc.get(i)] = idx;
				if(ker.isActive(cc.get(i))){
					tV = true;
				}
			}
			if(tV){
				min++;
			}else{
				ccWithNoTV.add(cc);
			}idx++;
		}
		int max = ConnectivityFinder.findCCOf(g.getKernelGraph()).size() + g.getEnvelopOrder() - g.getKernelOrder();
		// TODO couplage generalise
		
		// PRUNING
		// --- Bounds
		k.updateLowerBound(min, this);
		k.updateUpperBound(max, this);
		if(k.instantiated()){
			// --- Max
			if(k.getValue()==max){
				INeighbors nei;
				for(int i = env.nextValue(0); i>=0; i = env.nextValue(i+1)){
					nei = g.getEnvelopGraph().getNeighborsOf(i);
					for(int j = nei.getFirstElement(); j>=0; j = nei.getNextElement()){
						if(ccof[i]!=ccof[j] && i!=j){
							g.removeArc(i, j, this);
						}
					}
				}
			}
			// --- Min
			if(k.getValue()==min){
				// --- remove nodes of CC with no T-Vertex
				for(TIntArrayList cc:ccWithNoTV){
					for(int i=0; i<cc.size(); i++){
						g.removeNode(cc.get(i), this);
					}
				}
				// --- add articulation points that split at least two TVertices to the kernel
				BitSet ap = envCcObj.getArticulationPoints();
				for(int i=ap.nextSetBit(0); i>=0; i = ap.nextSetBit(i+1)){
					g.enforceNode(i, this);
				}
				// --- add isthmus that split at least two TVertices to the kernel
				TIntArrayList isthmus = envCcObj.getIsthmus();
				int from,to;
				for(int i=0; i<isthmus.size(); i++){
					from = isthmus.get(i)/n-1;
					to   = isthmus.get(i)%n;
					if(g instanceof DirectedGraphVar){
						DirectedGraphVar dig = (DirectedGraphVar) g;
						if (dig.getEnvelopGraph().arcExists(from, to) && !dig.getEnvelopGraph().arcExists(to, from) ){
							g.enforceArc(from, to, this);
						}else {
							g.enforceArc(to, from, this);
						}
					}else{
						g.enforceArc(from, to, this);
					}
				}
			}
		}
	}

	//***********************************************************************************
	// INFO
	//***********************************************************************************

	@Override
	public int getPropagationConditions(int vIdx) {
		return EventType.REMOVENODE.mask +  EventType.REMOVEARC.mask +  EventType.ENFORCENODE.mask +  EventType.ENFORCEARC.mask + EventType.ALL_MASK();
	}

	@Override
	public ESat isEntailed() {
		return ESat.UNDEFINED;
	}
	
	//***********************************************************************************
	// CONNECTED COMPONENTS AND ARTICULATION POINTS IN ONE DFS
	//***********************************************************************************
	private BitSet leadToActiveNode;
	private IActiveNodes actKer;
	private BitSet notFirst;
	private BitSet notOpenedNodes;
	private INeighbors[] neighbors;
	private int[] numOfNode;
	private int[] ND;
	private int[] nodeOfNum;
	private int[] p;
	private int[] inf;
	private int[] L;
	private int[] H;
	
	private void initDataStructure(){
		int nb = g.getEnvelopGraph().getNbNodes();
		p = new int[nb];
		numOfNode = new int[nb];
		nodeOfNum = new int[nb];
		inf = new int[nb];
		ND  = new int[nb];
		L  = new int[nb];
		H  = new int[nb];
		leadToActiveNode = new BitSet(nb);
		actKer = g.getKernelGraph().getActiveNodes();
		neighbors = new INeighbors[nb];
		notOpenedNodes = new BitSet(nb);
		notFirst = new BitSet(nb);
	}
	
	private ConnectivityObject findAll(){
		ConnectivityObject co = new ConnectivityObject();
		leadToActiveNode.clear();
		notOpenedNodes.clear();
		notFirst.clear();
		for (int i = g.getEnvelopGraph().getActiveNodes().nextValue(0); i>=0; i = g.getEnvelopGraph().getActiveNodes().nextValue(i+1)) {
			p[i] = -1;
			neighbors[i] = g.getEnvelopGraph().getNeighborsOf(i);
			if(actKer.isActive(i)){
				notOpenedNodes.set(i);
			}
		}
		int first = 0;
		first = notOpenedNodes.nextSetBit(first);
		while(first>=0){
			firstAllOnOneCC(co, first);
			first = notOpenedNodes.nextSetBit(first);
		}
		return co;
	}
	
	private void firstAllOnOneCC(ConnectivityObject co, int start){
		co.newCC();
		int i = start;
		int k = 0;
		numOfNode[start] = k;
		nodeOfNum[k] = start;
		p[start] = start;
		notOpenedNodes.clear(start);
		int j=0,q;
		co.addCCNode(start);
		int nbRootChildren = 0;
		boolean notFinished = true;
		while(notFinished){
			if(notFirst.get(i)){
				j = neighbors[i].getNextElement();
			}else{
				j = neighbors[i].getFirstElement();
				notFirst.set(i);
			}
			if(j<0){
				if(i==start){notFinished = false;break;}
				boolean old = leadToActiveNode.get(i);
				leadToActiveNode.set(p[i],leadToActiveNode.get(p[i]) || old);
				q = inf[i];
				i = p[i];
				inf[i] = Math.min(q, inf[i]);
				if (q >= numOfNode[i] && i!=start && old){ 
					co.addArticulationPoint(i);// ARTICULATION POINT THAT SPLIT T-VERTEX DETECTED
				}
			}else{
				if (p[j]==-1) {
					p[j] = i;
					if (i == start){
						nbRootChildren++;
					}
					i = j;
					if(actKer.isActive(i)){
						leadToActiveNode.set(i);
					}
					notOpenedNodes.clear(i);
					k++;
					numOfNode[i] = k;
					nodeOfNum[k] = i;
					inf[i] = numOfNode[i];
					co.addCCNode(i);
				}else if(p[i]!=j){
					inf[i] = Math.min(inf[i], numOfNode[j]);
				}
			}
		}
		if (nbRootChildren>1){ 
			co.addArticulationPoint(i);
		}
		
		// POST ORDER PASS FOR FINDING ISTHMUS
		int n = neighbors.length;
		int currentNode;
		for(i=k; i>=0; i--){
			currentNode = nodeOfNum[i];
			ND[currentNode] = 1;
			L[currentNode]  = i;
			H[currentNode]  = i;
			for(int s=neighbors[currentNode].getFirstElement(); s>=0; s = neighbors[currentNode].getNextElement()){
				if (p[s]==currentNode){
					ND[currentNode] += ND[s];
					L[currentNode] = Math.min(L[currentNode], L[s]);
					H[currentNode] = Math.max(H[currentNode], H[s]);
				}else{
					L[currentNode] = Math.min(L[currentNode], numOfNode[s]);
					H[currentNode] = Math.max(H[currentNode], numOfNode[s]);
				}
				if (p[s]==currentNode && L[s]>= numOfNode[s] && H[s] < numOfNode[s]+ND[s] && co.isArticulationPoints(s) && co.isArticulationPoints(currentNode)){ 
					co.addIsthmus((currentNode+1)*n+s); // ISTHMUS DETECTED
				}
			}
		}
	}
}