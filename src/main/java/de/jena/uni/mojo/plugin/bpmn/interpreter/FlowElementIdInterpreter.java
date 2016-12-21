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
package de.jena.uni.mojo.plugin.bpmn.interpreter;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.activiti.designer.bpmn2.model.FlowElement;
import org.activiti.designer.bpmn2.model.SequenceFlow;

import de.jena.uni.mojo.interpreter.AbstractEdge;
import de.jena.uni.mojo.interpreter.IdInterpreter;
import de.jena.uni.mojo.plugin.bpmn.analysis.transform.FlowStore;

/**
 * The flow element id interpreter is a specific IdInterpreter. It allows the
 * extraction of ids of BPMN flow elements.
 * 
 * @author Dipl.-Inf. Thomas M. Prinz
 * 
 */
public class FlowElementIdInterpreter extends IdInterpreter {

	/**
	 * A flow store that contains the information about all flows within
	 * the process.
	 */
	private final FlowStore flowStore;

	/**
	 * The constructor.
	 * 
	 * @param flowStore
	 *            The flow store of the process.
	 */
	public FlowElementIdInterpreter(FlowStore flowStore) {
		this.flowStore = flowStore;
	}

	@Override
	public String extractId(Object obj) {
		FlowElement flowElement = (FlowElement) obj;
		return flowElement.getId();
	}

	@Override
	public String extractPath(Collection<Object> nodes) {

		String idString = "";
		for (final Object node : nodes) {
			FlowElement element = (FlowElement) node;
			final Set<SequenceFlow> outgoingFlows = flowStore
					.getOutgoingFlows(element);

			if (outgoingFlows == null) {
				continue;
			}

			// Find sequence flow outgoing from the node that has one of the
			// other nodes as a target
			for (final SequenceFlow sequenceFlow : outgoingFlows) {
				for (final Object possibleTarget : nodes) {
					if (sequenceFlow.getTargetRef().getId() == ((FlowElement) possibleTarget)
							.getId()) {
						if (idString.isEmpty()) {
							idString = sequenceFlow.getId();
						} else {
							idString += "," + sequenceFlow.getId();
						}
					}
				}
			}
		}

		if (idString.endsWith(",")) {
			idString = idString.substring(0, idString.length() - 1);
		}
		return idString;
	}

	@Override
	public String extractPath(List<AbstractEdge> edges) {

		String idString = "";
		for (final AbstractEdge edge : edges) {
			FlowElement element = (FlowElement) edge.source;
			final Set<SequenceFlow> outgoingFlows = flowStore
					.getOutgoingFlows(element);

			if (outgoingFlows == null) {
				continue;
			}

			// Find sequence flow outgoing from the node that has one of the
			// other nodes as a target
			for (final SequenceFlow sequenceFlow : outgoingFlows) {
				if (sequenceFlow.getTargetRef().getId() == ((FlowElement) edge.target)
						.getId()) {
					if (idString.isEmpty()) {
						idString = sequenceFlow.getId();
					} else {
						idString += "," + sequenceFlow.getId();
					}
				}
			}
		}

		if (idString.endsWith(",")) {
			idString = idString.substring(0, idString.length() - 1);
		}
		return idString;
	}

}
