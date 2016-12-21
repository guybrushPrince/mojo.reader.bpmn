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
package de.jena.uni.mojo.plugin.bpmn.parser.bpmn;

import java.util.ArrayList;
import java.util.List;

import org.activiti.designer.bpmn2.model.FlowElement;
import org.activiti.designer.bpmn2.model.Message;
import org.activiti.designer.bpmn2.model.Pool;
import org.activiti.designer.bpmn2.model.Process;
import org.activiti.designer.bpmn2.model.Signal;

/**
 * An extracted part of the activiti designer bpmn 2.0 model. It defines the
 * BPMN to Memory Model transformation. It is a changed.
 * 
 * @author Dipl.-Inf. Thomas M. Prinz
 * 
 */
public class Bpmn2MemoryModel {

	/**
	 * A list of defined processes.
	 */
	protected List<Process> processes = new ArrayList<Process>();

	/**
	 * A list of flow elements.
	 */
	protected List<FlowElement> clipboard = new ArrayList<FlowElement>();

	/**
	 * A list of signals.
	 */
	protected List<Signal> signals = new ArrayList<Signal>();

	/**
	 * A list of messages flows.
	 */
	protected List<Message> messages = new ArrayList<Message>();

	/**
	 * A list of pools.
	 */
	protected List<Pool> pools = new ArrayList<Pool>();

	/**
	 * The namespace of the target.
	 */
	protected String targetNamespace;

	/**
	 * Returns the main process.
	 * 
	 * @return The process.
	 */
	public Process getMainProcess() {
		Process process = getProcess(null);
		if (process == null) {
			process = new Process();
			process.setName("process1");
			process.setId("process1");
			addProcess(process);
		}

		return process;
	}

	/**
	 * Returns a sub process which is defined in a pool.
	 * 
	 * @param poolRef
	 *            The pool reference as string.
	 * @return The found process or null.
	 */
	public Process getProcess(String poolRef) {
		for (Process process : processes) {
			boolean foundPool = false;
			for (Pool pool : pools) {
				if (pool.getProcessRef().equalsIgnoreCase(process.getId())) {

					if (poolRef != null) {
						if (pool.getId().equalsIgnoreCase(poolRef)) {
							foundPool = true;
						}
					} else {
						foundPool = true;
					}
				}
			}

			if (poolRef == null && foundPool == false) {
				return process;
			} else if (poolRef != null && foundPool == true) {
				return process;
			}
		}

		return null;
	}

	/**
	 * Get all processes.
	 * 
	 * @return A list of processes.
	 */
	public List<Process> getProcesses() {
		return processes;
	}

	/**
	 * Add a process.
	 * 
	 * @param process
	 *            The process to add.
	 */
	public void addProcess(Process process) {
		processes.add(process);
	}

	/**
	 * The flow elements on the clip board.
	 * 
	 * @return A list of flow elements.
	 */
	public List<FlowElement> getClipboard() {
		return clipboard;
	}

	/**
	 * Set the clip board by setting flow elements.
	 * 
	 * @param clipboard
	 *            A list of flow elements to set.
	 */
	public void setClipboard(List<FlowElement> clipboard) {
		this.clipboard = clipboard;
	}

	/**
	 * Get the signals.
	 * 
	 * @return A list of signals.
	 */
	public List<Signal> getSignals() {
		return signals;
	}

	/**
	 * Set the signals.
	 * 
	 * @param signals
	 *            A list of signals to set.
	 */
	public void setSignals(List<Signal> signals) {
		this.signals = signals;
	}

	/**
	 * Get the messages.
	 * 
	 * @return A list of messages.
	 */
	public List<Message> getMessages() {
		return messages;
	}

	/**
	 * Set the messages
	 * 
	 * @param messages
	 *            A list of messages to set.
	 */
	public void setMessages(List<Message> messages) {
		this.messages = messages;
	}

	/**
	 * Get the pools.
	 * 
	 * @return A list of pools.
	 */
	public List<Pool> getPools() {
		return pools;
	}

	/**
	 * Set the pools.
	 * 
	 * @param pools
	 *            A list of pools to set.
	 */
	public void setPools(List<Pool> pools) {
		this.pools = pools;
	}

	/**
	 * Get the target namespace.
	 * 
	 * @return The target namespace.
	 */
	public String getTargetNamespace() {
		return targetNamespace;
	}

	/**
	 * Set the target namespace.
	 * 
	 * @param targetNamespace
	 *            The target namespace to set.
	 */
	public void setTargetNamespace(String targetNamespace) {
		this.targetNamespace = targetNamespace;
	}
}
