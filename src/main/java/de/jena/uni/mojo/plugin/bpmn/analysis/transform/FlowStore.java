/**
 * Copyright 2016 mojo Friedrich Schiller University Jena
 * 
 * This file is part of mojo.
 * 
 * mojo is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * mojo is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with mojo. If not, see <http://www.gnu.org/licenses/>.
 */
package de.jena.uni.mojo.plugin.bpmn.analysis.transform;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.activiti.designer.bpmn2.model.FlowElement;
import org.activiti.designer.bpmn2.model.FlowNode;
import org.activiti.designer.bpmn2.model.SequenceFlow;

/**
 * 
 * @author Dipl.-Inf. Thomas M. Prinz
 *
 */
public class FlowStore {	

	/**
	 * A map which holds for each flow node (key) its outgoing sequence flows
	 * (value)
	 */
	private final Map<FlowNode, Set<SequenceFlow>> outgoingFlowMap = new HashMap<>();

	/**
	 * Adds an outgoing sequence flow to the flow node.
	 * 
	 * @param start
	 *            The flow node where we have to add the sequence flow.
	 * @param outgoing
	 *            The outgoing sequence flow.
	 */
	public void putFlowMapping(final FlowNode start, final SequenceFlow outgoing) {
		Set<SequenceFlow> set = outgoingFlowMap.get(start);
		if (set == null) {
			set = new HashSet<>();
			outgoingFlowMap.put(start, set);
		}
		set.add(outgoing);
	}

	/**
	 * Get a set of outgoing sequence flows of a specific node.
	 * 
	 * @param node
	 *            The node for which the sequence flows should be determined.
	 * @return A set of sequence flows.
	 */
	public Set<SequenceFlow> getOutgoingFlows(final FlowElement node) {
		return outgoingFlowMap.get(node);
	}
	
}
