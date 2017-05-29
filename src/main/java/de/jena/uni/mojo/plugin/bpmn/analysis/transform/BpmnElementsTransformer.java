/**
 * Copyright 2013 mojo Friedrich Schiller University Jena
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.activiti.designer.bpmn2.model.EndEvent;
import org.activiti.designer.bpmn2.model.FlowElement;
import org.activiti.designer.bpmn2.model.FlowNode;
import org.activiti.designer.bpmn2.model.Gateway;
import org.activiti.designer.bpmn2.model.Process;
import org.activiti.designer.bpmn2.model.SequenceFlow;
import org.activiti.designer.bpmn2.model.StartEvent;
import org.activiti.designer.bpmn2.model.Task;

import de.jena.uni.mojo.analysis.CoreAnalysis;
import de.jena.uni.mojo.analysis.information.AnalysisInformation;
import de.jena.uni.mojo.error.Annotation;
import de.jena.uni.mojo.error.SelfLoopAnnotation;
import de.jena.uni.mojo.error.UnsupportedElementAnnotation;
import de.jena.uni.mojo.model.WGNode;
import de.jena.uni.mojo.model.WGNode.Type;
import de.jena.uni.mojo.model.WorkflowGraph;
import de.jena.uni.mojo.util.store.ElementStore;


/**
 * Basic transformer for the BPMN elements to {@link WorkflowGraph}s.
 * 
 * @author Norbert Spiess
 * @author Dipl.-Inf. Thomas M. Prinz
 * 
 */
public class BpmnElementsTransformer extends CoreAnalysis {

	/**
	 * The serial version UID.
	 */
	private static final long serialVersionUID = -8484701368785694739L;

	/**
	 * The element store.
	 */
	private final ElementStore elementStore;
	
	/**
	 * The flow store.
	 */
	private final FlowStore flowStore;

	/**
	 * A list of processes.
	 */
	private final List<Process> processes;

	/**
	 * A list of resulting workflow graphs.
	 */
	private List<WorkflowGraph> result;

	/**
	 * A list of produced failure annotations.
	 */
	private List<Annotation> annotations = new ArrayList<Annotation>();

	/**
	 * The constructor.
	 * 
	 * @param file
	 *            The file to read in.
	 * @param elementStore
	 *            The element store.
	 * @param processes
	 *            A list of processes.
	 * @param analysisInformation
	 *            An analysis information.
	 */
	public BpmnElementsTransformer(final String file,
			final ElementStore elementStore, final FlowStore flowStore,
			final List<Process> processes,
			final AnalysisInformation analysisInformation) {
		super(file, analysisInformation);
		this.elementStore = elementStore;
		this.flowStore = flowStore;
		this.processes = processes;		
	}

	@Override
	public List<Annotation> analyze() {
		// Transform the nodes within the processes.
		Map<WGNode, String> startProcessMap = transformNodes(processes);

		// Create a workflow graph on the base of it.
		final WorkflowGraphCreator workflowGraphCreator = new WorkflowGraphCreator(
				this.processName, elementStore, startProcessMap, this.reporter);

		// Add all annotations which will be created during the workflow
		// graph creation.
		this.annotations.addAll(workflowGraphCreator.compute());

		// Set the result
		this.result = workflowGraphCreator.getResult();

		return annotations;
	}

	/**
	 * Transforms each BPMN node in a workflow graph node.
	 * 
	 * @param processes
	 *            A list of processes.
	 * @return A map with the workflow graph nodes as keys and a string as
	 *         value.
	 */
	private Map<WGNode, String> transformNodes(final List<Process> processes) {
		// Create necessary lists and maps.
		final Map<WGNode, String> startProcessMap = new HashMap<>();
		final List<SequenceFlow> sequenceFlows = new ArrayList<>();
		final Map<FlowElement, WGNode> flowWGMap = new HashMap<>();

		// Iterate each process.
		for (final Process process : processes) {
			// Iterate each flow element.
			for (final FlowElement flowElement : process.getFlowElements()) {
				if (flowElement instanceof SequenceFlow) {
					// Store sequence flows to run over later when it is known
					// if there are unsupported nodes.
					sequenceFlows.add((SequenceFlow) flowElement);
					continue;
				}

				if (flowElement instanceof Task) {
					// handle all kind of tasks as activities
					createWGNodeForFlowElement(flowWGMap, flowElement,
							Type.ACTIVITY);
				} else if (flowElement instanceof StartEvent) {
					// the start event stays a start
					final WGNode node = createWGNodeForFlowElement(flowWGMap,
							flowElement, Type.START);
					startProcessMap.put(node, process.getId());
				} else if (flowElement instanceof EndEvent) {
					// the end event stays an end
					createWGNodeForFlowElement(flowWGMap, flowElement, Type.END);
				} else if (flowElement instanceof Gateway) {
					// gateways can't be defined yet, have to be analysed
					createWGNodeForFlowElement(flowWGMap, flowElement,
							Type.UNDEFINED);
				} else {
					// not supported elements are seen as activities
					WGNode node = createWGNodeForFlowElement(flowWGMap,
							flowElement, Type.ACTIVITY);

					UnsupportedElementAnnotation annotation = new UnsupportedElementAnnotation(
							this);

					annotation.addPrintableNode(node);
					annotations.add(annotation);
				}
			}
		}

		// Handle each found sequence flow.
		for (final SequenceFlow flow : sequenceFlows) {

			// Determine the flow node source and the
			// corresponding workflow graph node.
			final FlowNode sourceRef = flow.getSourceRef();
			final WGNode src = flowWGMap.get(sourceRef);
			// Determine the flow node target and the
			// corresponding workflow graph node.
			final FlowNode targetRef = flow.getTargetRef();
			final WGNode tgt = flowWGMap.get(targetRef);

			// If they are not null.
			if (src != null && tgt != null) {

				if (src.getId() == tgt.getId()) {
					// handle direct loops by adding a new activity in between
					WGNode activity = addActivityInBetween(sourceRef, src, tgt);

					SelfLoopAnnotation annotation = new SelfLoopAnnotation(this);

					annotation.addPrintableNode(activity);
					annotations.add(annotation);

					continue;
				}

				if (tgt.getPredecessors().contains(src)) {
					// duplicate direct flows are avoided through the addition
					// of activities
					addActivityInBetween(sourceRef, src, tgt);
					continue;
				} else {
					src.addSuccessor(tgt);
					tgt.addPredecessor(src);
				}
				this.flowStore.putFlowMapping(sourceRef, flow);
			}
		}
		return startProcessMap;
	}

	/**
	 * Create a workflow graph node for a given flow element.
	 * 
	 * @param flowWGMap
	 *            The workflow graph map.
	 * @param flowElement
	 *            The flow element to transform.
	 * @param type
	 *            The type of workflow graph node.
	 * @return A new workflow graph node.
	 */
	private WGNode createWGNodeForFlowElement(
			final Map<FlowElement, WGNode> flowWGMap,
			final FlowElement flowElement, final Type type) {
		final WGNode node = createNode(type);
		node.addProcessElement(flowElement);
		node.setCode(flowElement.getDocumentation());
		flowWGMap.put(flowElement, node);

		return node;
	}

	/**
	 * Create a workflow graph node based on a given type.
	 * 
	 * @param type
	 *            The type of the new workflow graph node.
	 * @return The new workflow graph node.
	 */
	private WGNode createNode(final Type type) {
		final WGNode node = elementStore.createNode(type);
		return node;
	}

	/**
	 * Add an activity between two given workflow graph nodes. The source
	 * reference is given for connection issues.
	 * 
	 * @param sourceRef
	 *            The source reference flow node.
	 * @param src
	 *            The source workflow graph node.
	 * @param tgt
	 *            The target workflow graph node.
	 * @return The workflow graph node which was placed in between.
	 */
	private WGNode addActivityInBetween(final FlowNode sourceRef,
			final WGNode src, final WGNode tgt) {
		final WGNode activity = elementStore.createNode(Type.ACTIVITY);
		src.addSuccessor(activity);
		activity.addPredecessor(src);
		activity.addSuccessor(tgt);
		tgt.addPredecessor(activity);

		// set flow element references
		activity.addProcessElement(sourceRef);

		return activity;
	}

	/**
	 * Returns the result, i.e., the list of workflow graphs.
	 * 
	 * @return A list of workflow graphs.
	 */
	public List<WorkflowGraph> getResult() {
		return result;
	}

}
