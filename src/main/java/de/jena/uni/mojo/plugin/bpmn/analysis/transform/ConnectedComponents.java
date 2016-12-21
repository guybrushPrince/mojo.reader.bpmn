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
import java.util.BitSet;
import java.util.List;

import de.jena.uni.mojo.model.WGNode;

/**
 * Connected components algorithm, taken from
 * http://algs4.cs.princeton.edu/41undirected/CC.java.html and adapted to the
 * given requirements. <br>
 * <br>
 * Copyright (C) 2012 Robert Sedgewick and Kevin Wayne <br>
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. <br>
 * <br>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details. <br>
 * <br>
 * See http://www.gnu.org/licenses/ for the GNU General Public License.
 * 
 * @author Norbert Spiess
 * 
 */
public class ConnectedComponents {

	/**
	 * Visited.get(v) = has vertex v been visited?
	 */
	private BitSet visited;

	/**
	 * id[v] = id of connected component containing v
	 */
	private int[] id;

	/**
	 * Number of connected components
	 */
	private int componentCount;

	/**
	 * Compute the connected components for a set of workflow graph nodes.
	 * 
	 * @param elements
	 *            A list of workflow graph nodes.
	 * @return A list of lists whereas each list is a component.
	 */
	public List<List<WGNode>> computeComponents(final List<WGNode> elements) {
		// Get the number of elements.
		final int amountElements = elements.size();
		// Set the number of components found.
		componentCount = 0;
		// Initialize the visited bit set.
		visited = new BitSet(amountElements);
		// Initialize the id array.
		id = new int[amountElements];

		// For each unvisited node perform a depth-first search.
		for (final WGNode wgNode : elements) {
			if (!visited.get(wgNode.getId())) {
				dfs(wgNode);
				componentCount++;
			}
		}

		// Create a new list for each component
		final List<List<WGNode>> components = new ArrayList<>(componentCount);
		for (int i = 0; i < componentCount; i++) {
			components.add(new ArrayList<WGNode>());
		}

		// Sort the elements into the corresponding list
		for (final WGNode node : elements) {
			final int componentId = id[node.getId()];
			components.get(componentId).add(node);
		}
		return components;
	}

	/**
	 * Performs a depth-first search starting in the given workflow graph node.
	 * However, already visited nodes in previous searches are not visited
	 * again.
	 * 
	 * @param wgNode
	 *            The workflow graph node.
	 */
	private void dfs(final WGNode wgNode) {
		final int nodeId = wgNode.getId();
		visited.set(nodeId);
		id[nodeId] = componentCount;
		for (final WGNode predecessor : wgNode.getPredecessors()) {
			if (!visited.get(predecessor.getId())) {
				dfs(predecessor);
			}
		}
		for (final WGNode successor : wgNode.getSuccessors()) {
			if (!visited.get(successor.getId())) {
				dfs(successor);
			}
		}
	}
}
