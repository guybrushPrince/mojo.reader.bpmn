/**
 * Copyright 2015 mojo Friedrich Schiller University Jena
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
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.activiti.designer.bpmn2.model.FlowElement;
import org.activiti.designer.bpmn2.model.FlowNode;
import org.activiti.designer.bpmn2.model.Process;
import org.activiti.designer.bpmn2.model.SequenceFlow;

import de.jena.uni.mojo.analysis.information.AnalysisInformation;
import de.jena.uni.mojo.error.Annotation;
import de.jena.uni.mojo.error.ParseAnnotation;
import de.jena.uni.mojo.plugin.bpmn.analysis.transform.BpmnElementsTransformer;
import de.jena.uni.mojo.plugin.bpmn.analysis.transform.FlowStore;
import de.jena.uni.mojo.plugin.bpmn.parser.BpmnParser;
import de.jena.uni.mojo.plugin.bpmn.parser.bpmn.Bpmn2MemoryModel;
import de.jena.uni.mojo.plugin.bpmn.parser.bpmn.SequenceFlowModel;
import de.jena.uni.mojo.reader.Reader;
import de.jena.uni.mojo.util.store.ElementStore;
import de.jena.uni.mojo.util.store.ErrorAndWarningStore;

/**
 * The BPMNReader is a simple own implementation of a BPMN parser. This
 * FullBPMNReader uses more advanced techniques from the Activiti designer.
 * 
 * @author Dipl.-Inf. Thomas Prinz
 * 
 */
public class FullBPMNReader extends Reader {

	/**
	 * The serial version UID.
	 */
	private static final long serialVersionUID = 3492046894715365053L;
	
	/**
	 * An input stream of the file.
	 */
	private InputStream input;

	/**
	 * The constructor defines a new full bpmn reader.
	 * 
	 * @param processName
	 * 			  The name of the process.
	 * @param stream
	 *            An xml string.
	 * @param analysisInformation
	 *            The analysis information.
	 * @param encoding
	 * 			  The encoding used in the stream.
	 */
	public FullBPMNReader(
			String processName, 
			String stream, 
			AnalysisInformation analysisInformation, 
			Charset encoding) {
		super(processName, analysisInformation);
		this.input = new ByteArrayInputStream(stream.getBytes(encoding));
	}

	/**
	 * The element store.
	 */
	private ElementStore elementStore;

	/**
	 * The flow store.
	 */
	private FlowStore flowStore;

	/**
	 * The error and warning store.
	 */
	private ErrorAndWarningStore store;

	/**
	 * A list of annotations that are found during reading.
	 */
	private List<Annotation> annotations = new ArrayList<Annotation>();

	@Override
	public List<Annotation> analyze() {

		// Define a new bpmn memory model
		final Bpmn2MemoryModel model = new Bpmn2MemoryModel();

		try {
			// Create a file input stream, which is used to define
			// an xml stream.
			XMLStreamReader xtr = XMLInputFactory.newInstance().createXMLStreamReader(input);

			// Parse the bpmn file.
			BpmnParser parser = new BpmnParser();
			parser.parseBpmn(xtr, model);

			// Transform the sequence flows.
			transformSequenceFlows(parser, model);

			// Define the element store.
			this.elementStore = new ElementStore();
			this.flowStore = new FlowStore();

			// Transform the bpmn elements.
			final BpmnElementsTransformer transformer = new BpmnElementsTransformer(
					this.processName, elementStore, flowStore,
					model.getProcesses(), this.reporter);

			// Add found annotations.
			annotations.addAll(transformer.compute());

			this.graphs = transformer.getResult();

		} catch (XMLStreamException e) {
			// Define a failure annotation..
			ParseAnnotation annotation = new ParseAnnotation(this);
			annotations.add(annotation);
		}

		return annotations;
	}

	/**
	 * Transforms sequence flow models of the memory model into real sequence
	 * flow objects.
	 * 
	 * @param parser
	 *            The bpmn parser which has parsed the bpmn file.
	 * @param model
	 *            The memory model which was filled by the parser.
	 */
	private void transformSequenceFlows(BpmnParser parser,
			Bpmn2MemoryModel model) {
		Map<String, FlowNode> map = new HashMap<String, FlowNode>();
		int counter = 0;
		for (Process p : model.getProcesses()) {
			List<FlowElement> remove = new ArrayList<FlowElement>();
			for (FlowElement f : p.getFlowElements()) {
				if (!(f instanceof SequenceFlow)) {
					map.put(f.getId(), (FlowNode) f);
				} else {
					remove.add(f);
				}
			}
			p.getFlowElements().removeAll(remove);
			remove.clear();
			for (SequenceFlowModel s : parser.sequenceFlowList) {
				FlowNode source = map.get(s.sourceRef);
				FlowNode target = map.get(s.targetRef);
				SequenceFlow flow = new SequenceFlow();
				flow.setId((s.id != "" && s.id != null ? s.id : "sf" + counter++));
				flow.setSourceRef(source);
				flow.setTargetRef(target);
				p.getFlowElements().add(flow);
			}
		}
	}

	/**
	 * The element store.
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
