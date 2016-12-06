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
package org.mojo.reader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
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
import org.mojo.analysis.information.AnalysisInformation;
import org.mojo.analysis.transform.BpmnElementsTransformer;
import org.mojo.error.Annotation;
import org.mojo.error.ParseAnnotation;
import org.mojo.parser.BpmnParser;
import org.mojo.parser.bpmn.Bpmn2MemoryModel;
import org.mojo.parser.bpmn.SequenceFlowModel;
import org.mojo.util.store.ElementStore;
import org.mojo.util.store.ErrorAndWarningStore;

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
	 * The constructor defines a new full bpmn reader.
	 * 
	 * @param file
	 *            The file to read from.
	 * @param analysisInformation
	 *            The analysis information.
	 */
	public FullBPMNReader(File file, AnalysisInformation analysisInformation) {
		super(file, analysisInformation);
	}

	/**
	 * The element store.
	 */
	private ElementStore elementStore;

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
			FileInputStream fileStream = new FileInputStream(file);
			final XMLInputFactory xif = XMLInputFactory.newInstance();
			InputStreamReader in = new InputStreamReader(fileStream, "UTF-8");
			XMLStreamReader xtr = xif.createXMLStreamReader(in);

			// Parse the bpmn file.
			BpmnParser parser = new BpmnParser();
			parser.parseBpmn(xtr, model);

			// Transform the sequence flows.
			transformSequenceFlows(parser, model);

			// Define the element store.
			this.elementStore = new ElementStore();

			// Transform the bpmn elements.
			final BpmnElementsTransformer transformer = new BpmnElementsTransformer(
					this.file.getAbsolutePath(), elementStore,
					model.getProcesses(), this.reporter);

			// Add found annotations.
			annotations.addAll(transformer.compute());

			this.graphs = transformer.getResult();

		} catch (FileNotFoundException | UnsupportedEncodingException
				| XMLStreamException e) {
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
				flow.setId("sf" + (counter++));
				flow.setSourceRef(source);
				flow.setTargetRef(target);
				p.getFlowElements().add(flow);
			}
		}
	}

	/**
	 * The element store.
	 * @return The element store.
	 */
	public ElementStore getElementStore() {
		return elementStore;
	}

	@Override
	public ErrorAndWarningStore getStore() {
		return store;
	}

}
