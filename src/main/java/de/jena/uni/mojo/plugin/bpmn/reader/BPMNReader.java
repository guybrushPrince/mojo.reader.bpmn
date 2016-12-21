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
package de.jena.uni.mojo.plugin.bpmn.reader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.activiti.designer.bpmn2.model.EndEvent;
import org.activiti.designer.bpmn2.model.ExclusiveGateway;
import org.activiti.designer.bpmn2.model.FlowElement;
import org.activiti.designer.bpmn2.model.FlowNode;
import org.activiti.designer.bpmn2.model.InclusiveGateway;
import org.activiti.designer.bpmn2.model.ParallelGateway;
import org.activiti.designer.bpmn2.model.Process;
import org.activiti.designer.bpmn2.model.SequenceFlow;
import org.activiti.designer.bpmn2.model.StartEvent;
import org.activiti.designer.bpmn2.model.UserTask;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.jena.uni.mojo.Mojo;
import de.jena.uni.mojo.analysis.information.AnalysisInformation;
import de.jena.uni.mojo.error.Annotation;
import de.jena.uni.mojo.error.ParseAnnotation;
import de.jena.uni.mojo.model.WorkflowGraph;
import de.jena.uni.mojo.plugin.bpmn.analysis.transform.BpmnElementsTransformer;
import de.jena.uni.mojo.plugin.bpmn.analysis.transform.FlowStore;
import de.jena.uni.mojo.reader.Reader;
import de.jena.uni.mojo.util.store.ElementStore;
import de.jena.uni.mojo.util.store.ErrorAndWarningStore;

/**
 * Extends the reader class and reads in a process from a bpmn file.
 * 
 * @author Dipl.-Inf. Thomas Prinz
 * 
 */
public class BPMNReader extends Reader {

	/**
	 * The serial version UID.
	 */
	private static final long serialVersionUID = -2621443063422584581L;

	/**
	 * The element store where all extracted elements are stored.
	 */
	private ElementStore elementStore;

	/**
	 * The flow store.
	 */
	private FlowStore flowStore;

	/**
	 * An error and warning store.
	 */
	private ErrorAndWarningStore store;

	/**
	 * The created annotations.
	 */
	private final List<Annotation> annotations = new ArrayList<Annotation>();

	/**
	 * The constructor sets the file and the analysis information reporter.
	 * 
	 * @param file
	 *            The file to read.
	 * @param reporter
	 *            The analysis information.
	 */
	public BPMNReader(File file, AnalysisInformation reporter) {
		super(file, reporter);
	}

	@Override
	public List<Annotation> analyze() {

		this.graphs = new ArrayList<WorkflowGraph>();

		if (Mojo.getCommand("VERBOSE").asBooleanValue())
			System.out.printf("read %s\n", file.getName());
		// Read in the file
		List<FlowElement> elements = new ArrayList<FlowElement>();
		try {
			elements.addAll(readFile(file));
		} catch (SAXException | IOException | ParserConfigurationException e) {
			ParseAnnotation annotation = new ParseAnnotation(this);
			annotations.add(annotation);

			return annotations;
		}

		if (Mojo.getCommand("VERBOSE").asBooleanValue())
			System.out.printf("\ttransform ");

		graphs.addAll(runTransformation(elements));

		return annotations;
	}

	/**
	 * Reads an xml string and extracts a list of flow elements. After that,
	 * they will be transformed into a workflow graph and added to the graphs.
	 * 
	 * @param xml
	 *            The XML string.
	 * @return A list of workflow graphs.
	 * @throws SAXException
	 *             When there is a failure during reading.
	 * @throws IOException
	 *             When there is a failure during reading.
	 * @throws ParserConfigurationException
	 *             When the parser is wrongly configured.
	 */
	public List<WorkflowGraph> readXMLString(final String xml)
			throws SAXException, IOException, ParserConfigurationException {
		List<WorkflowGraph> graphs = new ArrayList<WorkflowGraph>();

		if (Mojo.getCommand("VERBOSE").asBooleanValue())
			System.out.printf("read %s\n", xml);

		// Read in the file
		List<FlowElement> elements = readXML(xml);

		if (Mojo.getCommand("VERBOSE").asBooleanValue())
			System.out.printf("\ttransform ");
		graphs.addAll(runTransformation(elements));

		return graphs;
	}

	/**
	 * Transforms a list of flow elements into at least one workflow graph.
	 * 
	 * @param elements
	 *            The elements to transform.
	 * @return A list of workflow graphs.
	 */
	private List<WorkflowGraph> runTransformation(
			final List<FlowElement> elements) {

		elementStore = new ElementStore();
		flowStore = new FlowStore();

		final Process process = new Process();
		process.setFlowElements(elements);

		final BpmnElementsTransformer transformer = new BpmnElementsTransformer(
				file.getAbsolutePath(), elementStore, flowStore,
				Collections.singletonList(process), this.reporter);

		annotations.addAll(transformer.compute());

		return transformer.getResult();
	}

	/**
	 * Extracts flow elements directly from a file.
	 * 
	 * @param file
	 *            The origin file.
	 * @return A list of flow elements.
	 * @throws SAXException
	 *             When there is failure during parsing xml.
	 * @throws IOException
	 *             When there is failure during parsing xml.
	 * @throws ParserConfigurationException
	 *             When there is failure during parsing xml.
	 */
	private List<FlowElement> readFile(final File file) throws SAXException,
			IOException, ParserConfigurationException {

		List<FlowElement> elementList;
		elementList = createElementList(file);
		return elementList;

	}

	/**
	 * Extracts flow elements directly from an xml string.
	 * 
	 * @param xml
	 *            A string containing xml.
	 * @return A list of flow elements.
	 * @throws SAXException
	 *             When there is failure during parsing xml.
	 * @throws IOException
	 *             When there is failure during parsing xml.
	 * @throws ParserConfigurationException
	 *             When there is failure during parsing xml.
	 */
	private List<FlowElement> readXML(final String xml) throws SAXException,
			IOException, ParserConfigurationException {

		List<FlowElement> elementList;
		elementList = createElementList(xml);
		return elementList;
	}

	/**
	 * Extracts flow elements directly from an xml string.
	 * 
	 * @param xml
	 *            A string containing xml.
	 * @return A list of flow elements.
	 * @throws SAXException
	 *             When there is failure during parsing xml.
	 * @throws IOException
	 *             When there is failure during parsing xml.
	 * @throws ParserConfigurationException
	 *             When there is failure during parsing xml.
	 */
	private List<FlowElement> createElementList(final String xml)
			throws SAXException, IOException, ParserConfigurationException {

		ByteArrayInputStream in = new ByteArrayInputStream(xml.getBytes());

		final Document doc = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder().parse(in);

		return createFromDocument(doc);
	}

	/**
	 * Extracts flow elements directly from a file.
	 * 
	 * @param file
	 *            A file containing xml.
	 * @return A list of flow elements.
	 * @throws SAXException
	 *             When there is failure during parsing xml.
	 * @throws IOException
	 *             When there is failure during parsing xml.
	 * @throws ParserConfigurationException
	 *             When there is failure during parsing xml.
	 */
	private List<FlowElement> createElementList(final File file)
			throws SAXException, IOException, ParserConfigurationException {

		final Document doc = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder().parse(file);

		doc.getDocumentElement().normalize();

		return createFromDocument(doc);
	}

	/**
	 * Extracts flow elements directly from a document. This is the most general
	 * function to extract flow elements.
	 * 
	 * @param doc
	 *            The xml document.
	 * @return A list of flow elements.
	 * @throws SAXException
	 *             When there is failure during parsing xml.
	 * @throws IOException
	 *             When there is failure during parsing xml.
	 * @throws ParserConfigurationException
	 *             When there is failure during parsing xml.
	 */
	private List<FlowElement> createFromDocument(final Document doc)
			throws SAXException, IOException, ParserConfigurationException {

		// Create a map for storing information about parsed objects.
		final Map<String, FlowNode> elementMap = new HashMap<String, FlowNode>();
		// A list of flow elements which are extracted from the document.
		final List<FlowElement> elementList = new ArrayList<FlowElement>();

		// Normalize the document.
		doc.getDocumentElement().normalize();

		// Extract the sub processes.
		NodeList nodeLst = doc.getElementsByTagName("subProcess");
		if (nodeLst.getLength() > 0) {
			System.err.println("Process contains unsupported nodes yet!");
			return null;
		}

		// Extract the start events.
		nodeLst = doc.getElementsByTagName("startEvent");
		for (int s = 0; s < nodeLst.getLength(); s++) {
			final Node fstNode = nodeLst.item(s);

			if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
				final Element fstElmnt = (Element) fstNode;
				final String id = fstElmnt.getAttribute("id");
				final StartEvent startEvent = new StartEvent();
				startEvent.setId(id);
				elementMap.put(id, startEvent);
				elementList.add(startEvent);
			}
		}

		// Extract the end events.
		nodeLst = doc.getElementsByTagName("endEvent");
		for (int s = 0; s < nodeLst.getLength(); s++) {
			final Node fstNode = nodeLst.item(s);

			if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
				final Element fstElmnt = (Element) fstNode;
				final String id = fstElmnt.getAttribute("id");
				final EndEvent node = new EndEvent();
				node.setId(id);
				elementMap.put(id, node);
				elementList.add(node);
			}
		}

		// Extract the parallel gateways.
		nodeLst = doc.getElementsByTagName("parallelGateway");
		for (int s = 0; s < nodeLst.getLength(); s++) {
			final Node fstNode = nodeLst.item(s);

			if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
				final Element fstElmnt = (Element) fstNode;
				final String id = fstElmnt.getAttribute("id");
				final ParallelGateway node = new ParallelGateway();
				node.setId(id);
				elementMap.put(id, node);
				elementList.add(node);
			}
		}

		// Extract the exclusive gateways.
		nodeLst = doc.getElementsByTagName("exclusiveGateway");
		for (int s = 0; s < nodeLst.getLength(); s++) {
			final Node fstNode = nodeLst.item(s);

			if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
				final Element fstElmnt = (Element) fstNode;
				final String id = fstElmnt.getAttribute("id");
				final ExclusiveGateway node = new ExclusiveGateway();
				node.setId(id);
				elementMap.put(id, node);
				elementList.add(node);
			}
		}

		// Extract the inclusive gateways.
		nodeLst = doc.getElementsByTagName("inclusiveGateway");
		for (int s = 0; s < nodeLst.getLength(); s++) {
			final Node fstNode = nodeLst.item(s);

			if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
				final Element fstElmnt = (Element) fstNode;
				final String id = fstElmnt.getAttribute("id");
				final InclusiveGateway node = new InclusiveGateway();
				node.setId(id);
				elementMap.put(id, node);
				elementList.add(node);
			}
		}

		// Extract the tasks/activities.
		nodeLst = doc.getElementsByTagName("task");
		for (int s = 0; s < nodeLst.getLength(); s++) {
			final Node fstNode = nodeLst.item(s);

			if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
				final Element fstElmnt = (Element) fstNode;
				final String id = fstElmnt.getAttribute("id");
				final UserTask node = new UserTask();
				node.setId(id);
				elementMap.put(id, node);
				elementList.add(node);
			}
		}

		// Extract the sequence flows and their source and targets.
		// At this point, all sources and targets must be parsed before.
		nodeLst = doc.getElementsByTagName("sequenceFlow");
		for (int s = 0; s < nodeLst.getLength(); s++) {
			final Node fstNode = nodeLst.item(s);

			if (fstNode.getNodeType() == Node.ELEMENT_NODE) {
				final Element fstElmnt = (Element) fstNode;
				final String id = fstElmnt.getAttribute("id");
				final SequenceFlow node = new SequenceFlow();
				node.setId(id);
				node.setSourceRef(elementMap.get(fstElmnt
						.getAttribute("sourceRef")));
				node.setTargetRef(elementMap.get(fstElmnt
						.getAttribute("targetRef")));
				elementList.add(node);
			}
		}
		return elementList;
	}

	/**
	 * Return the element store.
	 * 
	 * @return The element store.
	 */
	public ElementStore getElementStore() {
		return elementStore;
	}

	/**
	 * Get the flow store.
	 * 
	 * @return The flow store.
	 */
	public FlowStore getFlowStore() {
		return this.flowStore;
	}

	@Override
	public ErrorAndWarningStore getStore() {
		return store;
	}

}
