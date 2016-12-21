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
import java.util.Set;

import org.activiti.designer.bpmn2.model.ExclusiveGateway;
import org.activiti.designer.bpmn2.model.FlowElement;
import org.activiti.designer.bpmn2.model.InclusiveGateway;
import org.activiti.designer.bpmn2.model.ParallelGateway;

import de.jena.uni.mojo.analysis.CoreAnalysis;
import de.jena.uni.mojo.analysis.information.AnalysisInformation;
import de.jena.uni.mojo.error.Annotation;
import de.jena.uni.mojo.error.GatewayIsActivityAnnotation;
import de.jena.uni.mojo.error.NoEndAnnotation;
import de.jena.uni.mojo.error.NoStartAnnotation;
import de.jena.uni.mojo.error.StartWithPredecessorAnnotation;
import de.jena.uni.mojo.error.UnconnectedAnnotation;
import de.jena.uni.mojo.error.UnsupportedElementAnnotation;
import de.jena.uni.mojo.info.IPluginStrings;
import de.jena.uni.mojo.model.WGNode;
import de.jena.uni.mojo.model.WGNode.Type;
import de.jena.uni.mojo.model.WorkflowGraph;
import de.jena.uni.mojo.util.store.ElementStore;

/**
 * Class for creating {@link WorkflowGraph}s out of the stored list of elements
 * in the {@link ElementStore}.
 * 
 * @author Norbert Spiess
 * @author Dipl.-Inf. Thomas M. Prinz
 * 
 */
class WorkflowGraphCreator extends CoreAnalysis {

	/**
	 * The serial version UID.
	 */
	private static final long serialVersionUID = -2015747346418322565L;

	/**
	 * The start nodes of the workflow graph.
	 */
	private List<WGNode> starts;

	/**
	 * The nodes without a predecessor.
	 */
	private List<WGNode> nodesWithoutPred;

	/**
	 * The end nodes of the workflow graph.
	 */
	private List<WGNode> ends;

	/**
	 * The latest produced workflow graph.
	 */
	private WorkflowGraph latestGraph;

	/**
	 * The element store.
	 */
	private final ElementStore elementStore;

	/**
	 * A given workflow graph node to string map.
	 */
	private final Map<WGNode, String> startProcessMap;

	/**
	 * A list of all produced workflow graphs.
	 */
	private List<WorkflowGraph> result;

	/**
	 * A list of produced annotations.
	 */
	private List<Annotation> annotations = new ArrayList<Annotation>();

	/**
	 * The constructor.
	 * 
	 * @param file
	 *            The file where all the graphs belongs to.
	 * @param elementStore
	 *            The element store.
	 * @param startProcessMap
	 *            A workflow graph node to string map.
	 * @param analysisInformation
	 *            Analysis information.
	 */
	public WorkflowGraphCreator(final String file,
			final ElementStore elementStore,
			final Map<WGNode, String> startProcessMap,
			final AnalysisInformation analysisInformation) {
		super(file, analysisInformation);
		this.elementStore = elementStore;
		this.startProcessMap = startProcessMap;
	}

	/**
	 * Create {@link WorkflowGraph}s out of the stored elements in the node
	 * list.<br>
	 * How it is done:<br>
	 * <ul>
	 * <li>Calculate connected components to see which elements belong to which
	 * graph</li>
	 * <li>For each component
	 * <ul>
	 * <li>process the nodes</li>
	 * <ul>
	 * <li>add forks after a node (start, task) if it has more than one sucessor
	 * </li>
	 * <li>if a task has multiple predecessors, add a merge in front of it</li>
	 * <li>identify gateways that are marked as undefined</li>
	 * <ul>
	 * <li>decide on spreading or combining node respective on the type</li>
	 * <li>change gateways to activities if they have one incoming and one
	 * outgoing edge</li>
	 * </ul>
	 * <li>handle multiple incoming flow of nodes for end nodes</li>
	 * </ul>
	 * <li>combine start nodes and nodes without predecessors to one single
	 * start node</li>
	 * <ul>
	 * <li>starts in one pool get their own split in front of them</li>
	 * <li>starts in different pools are forked to as for nodes without
	 * predecessors</li>
	 * <li>if there is no start node because there is a loop, break for this
	 * graph and use the next one</li>
	 * </ul>
	 * <li>combine end nodes and nodes without sucessors to one single end node</li>
	 * </ul> </ul>
	 * 
	 * @return a list of {@link WorkflowGraph}s with one start, one end and
	 *         possible nodes inbetween
	 */
	public List<Annotation> analyze() {
		// Determine connected components where each component is a separate
		// workflow graph.
		final ConnectedComponents cc = new ConnectedComponents();
		final List<List<WGNode>> components = cc.computeComponents(elementStore
				.getNodeList());

		// If there are more than one component, we inform the user.
		if (components.size() > 1) {
			UnconnectedAnnotation annotation = new UnconnectedAnnotation(this);
			annotation.addInvolvedNodes(components.get(0));
			annotations.add(annotation);
		}

		// Construct a final list of workflow graphs.
		final List<WorkflowGraph> graphs = new ArrayList<>(components.size());

		// For each component, produce a workflow graph.
		for (final List<WGNode> graphElements : components) {
			// Create a new list of start nodes, nodes without predecessors, end
			// nodes
			// and a new workflow graph.
			starts = new ArrayList<>();
			nodesWithoutPred = new ArrayList<>();
			ends = new ArrayList<>();
			latestGraph = new WorkflowGraph();

			// Create the single workflow graph based on the elements.
			createSingleGraph(graphElements);
			if (hasNoNodeLeft()) {
				// Can happen if subgraph had only one element which is not
				// supported and therefore ignored
				continue;
			}
			// Combine start and end nodes.
			if (combineStartNodes()) {
				if (combineEndNodes()) {
					graphs.add(latestGraph);
				}
			}
		}

		// The result are the graphs.
		this.result = graphs;

		return annotations;
	}

	/**
	 * Checks whether the workflow graph consists only of one node.
	 * 
	 * @return True, if that is the fact. False, otherwise.
	 */
	private boolean hasNoNodeLeft() {
		return starts.isEmpty() && ends.isEmpty()
				&& latestGraph.getNodeList().isEmpty();
	}

	/**
	 * If the end node has more than one predecessor, we split the end node in
	 * separated end nodes.
	 * 
	 * @param oldEnd
	 *            The end node with more than one predecessor,
	 */
	private void simulateEndPoints(final WGNode oldEnd) {
		final List<WGNode> predecessors = new ArrayList<>(
				oldEnd.getPredecessors());
		/*
		 * simulate new end points for each relationship except the first one
		 * (to keep the old end node)
		 */
		for (int i = 1; i < predecessors.size(); i++) {
			final WGNode pred = predecessors.get(i);
			oldEnd.removePredecessor(pred);
			pred.removeSuccessor(oldEnd);
			final WGNode newEnd = createNode(Type.END);
			pred.addSuccessor(newEnd);
			newEnd.addPredecessor(pred);
			// set the reference to the same end element
			newEnd.addProcessElements(oldEnd.getProcessElements());
			ends.add(newEnd);
		}

	}

	/**
	 * Creates a new workflow graph node with a given type.
	 * 
	 * @param type
	 *            The type of the new workflow graph node.
	 * @return The new workflow graph node with the given type.
	 */
	private WGNode createNode(final Type type) {
		return elementStore.createNode(type);
	}

	/**
	 * Combines more than one end node to a single one.
	 * 
	 * @return True if the combination does not fails. False, otherwise.
	 * @author changed by Dipl.-Inf. Thomas M. Prinz
	 */
	private boolean combineEndNodes() {
		// @author changed by Dipl.-Inf. Thomas Prinz
		if (ends.isEmpty()) {
			// No end node found, error. Annotate the process.
			NoEndAnnotation annotation = new NoEndAnnotation(this);

			annotation.addInvolvedNodes(latestGraph.getNodeList());

			return false;
		} else if (ends.size() == 1) {
			// There is only one end node. Do nothing.
			latestGraph.setEnd(ends.get(0));
		} else {
			// Create a single end node
			WGNode end = createNode(Type.END);
			// Create an OR join
			WGNode orjoin = createNode(Type.OR_JOIN);
			latestGraph.addNode(orjoin);
			// Direct all old end nodes to the OR join
			for (WGNode node : ends) {
				if (node.getPredecessors().size() > 1) {
					node.setType(Type.OR_JOIN);
				} else {
					node.setType(Type.ACTIVITY);
				}
				orjoin.addPredecessor(node);
				node.addSuccessor(orjoin);
				latestGraph.addNode(node);

				// Add the process nodes of the old end node to
				// the new or-join.
				final Set<Object> bpmnElements = node.getProcessElements();
				end.addProcessElements(bpmnElements);
				orjoin.addProcessElements(bpmnElements);
			}
			// Direct the new OR join to the end node
			orjoin.addSuccessor(end);
			end.addPredecessor(orjoin);
			latestGraph.setEnd(end);
		}

		return true;
	}

	/**
	 * Places an end node after the given node.
	 * 
	 * @param wgNode
	 *            The node whereas on its end a end node is placed.
	 */
	private void addEndAfter(final WGNode wgNode) {
		final WGNode end = createNode(Type.END);
		wgNode.addSuccessor(end);
		end.addPredecessor(wgNode);
		end.addProcessElements(wgNode.getProcessElements());
		ends.add(end);
	}

	/**
	 * Combine more than one start nodes to a single one. This is based on BPMN
	 * semantics.
	 * 
	 * @return True whether the combination success. False, if not.
	 */
	private boolean combineStartNodes() {
		int amountStarts = starts.size();
		final int amountWithoutPred = nodesWithoutPred.size();

		// Only case where no start node has to be created
		if (amountStarts == 1 && amountWithoutPred == 0) {
			latestGraph.setStart(starts.get(0));
			return true;
		}

		// Figure out what start nodes lay together in one process/pool
		final Map<String, List<WGNode>> startGroupMap = new HashMap<>();
		for (final WGNode start : starts) {
			final String processId = startProcessMap.get(start);
			List<WGNode> list = startGroupMap.get(processId);
			if (list == null) {
				list = new ArrayList<>();
				startGroupMap.put(processId, list);
			}
			list.add(start);
			start.setType(Type.ACTIVITY);
			latestGraph.addNode(start);
		}

		// Fork into each group of 2 or more start nodes in the same process
		// with a split
		for (final List<WGNode> list : startGroupMap.values()) {
			if (list.size() > 1) {
				final WGNode split = addForkBefore(list, Type.SPLIT);
				nodesWithoutPred.add(split);
				amountStarts -= list.size();
				starts.removeAll(list);
			}
		}

		// Create the new start node in front of everything
		final WGNode start = createNode(Type.START);
		latestGraph.setStart(start);

		final List<WGNode> nodeList = new ArrayList<>(starts);
		nodeList.addAll(nodesWithoutPred);
		if (nodeList.size() > 1) {
			// Forking into several nodes (start splits, starts and nodes
			// without predecessors) in a parallel way to reach the splits, the
			// starts alone in one process and all nodes without predecessor
			final WGNode fork = addForkBefore(nodeList, Type.FORK);
			start.addSuccessor(fork);
			fork.addPredecessor(start);
			start.addProcessElements(fork.getProcessElements());
		} else if (nodeList.size() > 0) {
			// Only one node is a starting node, no fork necessary
			final WGNode node = nodeList.get(0);
			start.addSuccessor(node);
			node.addPredecessor(start);
			start.addProcessElements(node.getProcessElements());
		} else {
			// No starting node found, error. Create an annotation.
			NoStartAnnotation annotation = new NoStartAnnotation(this);

			annotation.addPrintableNodes(nodeList);
			annotation.addInvolvedNodes(latestGraph.getNodeList());

			return false;
		}

		return true;
	}

	/**
	 * Add a single fork/split node before a list/set of other nodes.
	 * 
	 * @param nodes
	 *            The list of nodes.
	 * @param type
	 *            The type of the forking node.
	 * @return The new created fork node.
	 */
	private WGNode addForkBefore(final List<WGNode> nodes, final Type type) {
		final WGNode forkingNode = createNode(type);
		forkingNode.addSuccessors(nodes);
		for (final WGNode wgNode : nodes) {
			wgNode.addPredecessor(forkingNode);
			forkingNode.addProcessElements(wgNode.getProcessElements());
		}

		latestGraph.addNode(forkingNode);
		return forkingNode;
	}

	/**
	 * Create a single workflow graph.
	 * 
	 * @param graphNodes
	 *            The nodes of the graph.
	 */
	private void createSingleGraph(final List<WGNode> graphNodes) {
		for (final WGNode node : graphNodes) {
			switch (node.getType()) {
			case START:
				handleStart(node);
				break;
			case END:
				handleEnd(node);
				break;
			case ACTIVITY:
				handleActivity(node);
				break;
			case UNDEFINED:
				handleUndefinedGateway(node);
				break;
			default:
				throw new IllegalArgumentException(
						IPluginStrings.UNEXPECTED_TYPE + node.getType());
			}
		}
	}

	/**
	 * Identify gateway type and handle incoming and outgoing edges.
	 * <ul>
	 * <li>Handle as {@link Type#ACTIVITY} if 0 or 1 incoming edge and 0 or 1
	 * outgoing edge</li>
	 * <li>Create a start node if no predecessor exists</li>
	 * <li>Create an end node if no sucessor exists</li>
	 * <li>Split into 2 fork/split and join/merge nodes respectively on multiple
	 * in and outgoing edges</li>
	 * </ul>
	 * 
	 * @param node
	 *            The node which is undefined.
	 */
	private void handleUndefinedGateway(final WGNode node) {
		// Get the successors and predecessors.
		final List<WGNode> successors = node.getSuccessors();
		final List<WGNode> predecessors = node.getPredecessors();

		// Set the joining and splitting node type.
		Type joinType = null;
		Type splitType = null;
		final FlowElement flowElement = (FlowElement) node.getProcessElements()
				.iterator().next();
		if (flowElement instanceof ExclusiveGateway) {
			joinType = Type.MERGE;
			splitType = Type.SPLIT;
		} else if (flowElement instanceof ParallelGateway) {
			joinType = Type.JOIN;
			splitType = Type.FORK;
		} else if (flowElement instanceof InclusiveGateway) {
			joinType = Type.OR_JOIN;
			splitType = Type.OR_FORK;
		} else {
			// The element is not supported. Annotate it on the process.
			UnsupportedElementAnnotation annotation = new UnsupportedElementAnnotation(
					this);

			annotation.addPrintableNode(node);
			annotations.add(annotation);

			// clear relationships with other nodes
			for (final WGNode wgNode : predecessors) {
				wgNode.removeSuccessor(node);
			}
			for (final WGNode wgNode : successors) {
				wgNode.removePredecessor(node);
			}
			node.clearPredecessors();
			node.clearSuccessors();
			return;
		}

		// Get the number of successors and predecessors
		final int amountSucs = successors.size();
		final int amountPreds = predecessors.size();

		if (amountPreds < 2) {
			boolean noStartNeeded = true;
			boolean noEndNeeded = true;
			// If gateway has 0 or 1 predecessor it is either a split or an
			// activity
			if (amountPreds < 1) {
				// if it has no predecessors, mark it as a node that needs a
				// start in front
				nodesWithoutPred.add(node);
				noStartNeeded = false;
			}
			if (amountSucs < 2) {
				node.setType(Type.ACTIVITY);
				if (amountSucs < 1) {
					// it gets an end node if it has no sucessor yet
					addEndAfter(node);
					noEndNeeded = false;
				}
				if (noStartNeeded && noEndNeeded) {
					GatewayIsActivityAnnotation annotation = new GatewayIsActivityAnnotation(
							this);
					annotation.addPrintableNode(node);
					annotations.add(annotation);
				}
			} else {
				// one incoming and multiple outgoing -> split/fork
				node.setType(splitType);
			}
		} else {
			// Multiple incoming edges (predecessors). Should be join/merge
			if (amountSucs < 1) {
				// on 0 outgoing, add an end
				addEndAfter(node);
			} else if (amountSucs > 1) {
				/*
				 * multiple incoming and multiple outgoing -> set node as
				 * join/merge and add a fork/split inbetween the node and the
				 * sucessors
				 */
				// addWarning(IPluginStrings.WARNING_GATEWAY_SPLITTED_UP, node);
				addForkAfterNode(node, successors, splitType);
			}
			// no matter how many outgoing edges, this node is seen as
			// merge/join
			node.setType(joinType);
		}
		latestGraph.addNode(node);
	}

	/**
	 * Handle a simple activity.
	 * 
	 * @param node
	 *            The node which is an activity.
	 */
	private void handleActivity(final WGNode node) {
		latestGraph.addNode(node);

		final List<WGNode> successors = node.getSuccessors();
		final List<WGNode> predecessors = node.getPredecessors();
		final int amountSucs = successors.size();
		final int amountPreds = predecessors.size();

		if (amountPreds == 0) {
			nodesWithoutPred.add(node);
		} else if (amountPreds > 1) {
			// add merge in front of it, defined by BPMN
			addJoinBeforeNode(node, predecessors, Type.MERGE);
		}

		if (amountSucs == 0) {
			addEndAfter(node);
		} else if (amountSucs > 1) {
			addForkAfterNode(node, successors, Type.FORK);
		}
	}

	/**
	 * Handle the end nodes.
	 * 
	 * @param node
	 *            The end node.
	 */
	private void handleEnd(final WGNode node) {
		ends.add(node);
		final int amountPreds = node.getPredecessors().size();
		if (amountPreds > 0) {
			// simulate multiple incoming flows for end nodes with additional
			// end nodes
			simulateEndPoints(node);
		} else {
			nodesWithoutPred.add(node);
		}
	}

	/**
	 * Handle the start nodes.
	 * 
	 * @param node
	 *            The start node.
	 */
	private void handleStart(final WGNode node) {
		final List<WGNode> successors = node.getSuccessors();
		final List<WGNode> predecessors = node.getPredecessors();
		final int amountSucs = successors.size();
		final int amountPreds = predecessors.size();

		if (amountPreds > 0) {
			StartWithPredecessorAnnotation annotation = new StartWithPredecessorAnnotation(
					this);
			annotation.addPrintableNode(node);
			annotations.add(annotation);

			if (amountPreds > 1) {
				addJoinBeforeNode(node, predecessors, Type.MERGE);
			}
			node.setType(Type.ACTIVITY);
			latestGraph.addNode(node);
		} else {
			starts.add(node);
		}

		if (amountSucs == 0) {
			addEndAfter(node);
		} else if (amountSucs > 1) {
			addForkAfterNode(node, successors, Type.FORK);
		}
	}

	/**
	 * Adds a fork node after a node.
	 * 
	 * @param node
	 *            The node to add a fork after.
	 * @param successors
	 *            A list of successor nodes.
	 * @param type
	 *            The type of the forking node.
	 * @return The new created fork node.
	 */
	private WGNode addForkAfterNode(final WGNode node,
			final List<WGNode> successors, final Type type) {

		// Create the forking node.
		final WGNode forkingNode = createNode(type);

		// Remove the predecessor of each successor. After
		// that, we connect the forking node with both.
		for (final WGNode suc : successors) {
			suc.removePredecessor(node);
			suc.addPredecessor(forkingNode);
			forkingNode.addSuccessor(suc);
		}

		node.clearSuccessors();
		node.addSuccessor(forkingNode);
		forkingNode.addPredecessor(node);

		forkingNode.addProcessElements(node.getProcessElements());

		latestGraph.addNode(forkingNode);
		return forkingNode;
	}

	/**
	 * Adds a join before a list of nodes.
	 * 
	 * @param node
	 *            The node.
	 * @param predecessors
	 *            The list of predecessors of this node.
	 * @param type
	 *            The type of the joining node.
	 */
	private void addJoinBeforeNode(final WGNode node,
			final List<WGNode> predecessors, final Type type) {
		final WGNode join = createNode(type);
		// The single removal of the predecessors from the node and no clear is
		// done because there are cases where not all predecessors have to be
		// removed (merging of incoming edges for end nodes)
		for (final WGNode pred : new ArrayList<WGNode>(predecessors)) {
			pred.removeSuccessor(node);
			pred.addSuccessor(join);
			join.addPredecessor(pred);
			node.removePredecessor(pred);
		}

		node.addPredecessor(join);
		join.addSuccessor(node);

		join.addProcessElements(node.getProcessElements());

		latestGraph.addNode(join);
	}

	/**
	 * Returns the result of this analysis.
	 * 
	 * @return A list of workflow graphs.
	 */
	public List<WorkflowGraph> getResult() {
		return this.result;
	}
}
