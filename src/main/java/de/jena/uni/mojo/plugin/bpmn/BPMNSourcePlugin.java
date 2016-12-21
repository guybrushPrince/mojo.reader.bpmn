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
package de.jena.uni.mojo.plugin.bpmn;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.jena.uni.mojo.Mojo;
import de.jena.uni.mojo.analysis.information.AnalysisInformation;
import de.jena.uni.mojo.error.Annotation;
import de.jena.uni.mojo.interpreter.IdInterpreter;
import de.jena.uni.mojo.model.WorkflowGraph;
import de.jena.uni.mojo.plugin.SourcePlugin;
import de.jena.uni.mojo.plugin.bpmn.interpreter.FlowElementIdInterpreter;
import de.jena.uni.mojo.plugin.bpmn.reader.FullBPMNReader;
import de.jena.uni.mojo.reader.Reader;
import de.jena.uni.mojo.verifier.Verifier;

/**
 * 
 * @author Dipl.-Inf. Thomas M. Prinz
 *
 */
public class BPMNSourcePlugin implements SourcePlugin {

	/**
	 * A storage for a full bpmn reader.
	 */
	private FullBPMNReader reader;
	
	@Override
	public String getName() {
		return "Mojo Source Plugin BPMN";
	}

	@Override
	public String getVersion() {
		return "1.0";
	}

	@Override
	public String getFileExtension() {
		return "bpmn.xml";
	}

	@Override
	public Reader getReader(File file, AnalysisInformation information) {
		this.reader = new FullBPMNReader(file, information);
		return reader;
	}

	@Override
	public IdInterpreter getIdInterpreter() {
		return new FlowElementIdInterpreter(reader.getFlowStore());
	}

	@Override
	public List<Annotation> verify(File bpmn, AnalysisInformation info) {
		// Create a list of errors.
		List<Annotation> errors = new ArrayList<Annotation>();

		if (bpmn.exists()) {
			// Create a new BPMN reader.
			FullBPMNReader reader = new FullBPMNReader(bpmn, info);

			// Read in the graphs.
			List<WorkflowGraph> graphs = null;

			errors.addAll(reader.compute());
			graphs = reader.getResult();

			// If there are no graphs within the file
			if (graphs == null)
				return errors;

			// Analyze each workflow graph.
			for (WorkflowGraph g : graphs) {

				// Create a new verifier.
				Verifier verifier = new Verifier(g, Mojo.createMap(g, Mojo.findMax(g)),
						info);

				// Start the time measurement
				info.startTimeMeasurement(g, "Verifier");

				errors.addAll(verifier.compute());

				// Stop the time measurement
				info.endTimeMeasurement(g, "Verifier");
			}
		}

		return errors;
	}

	@Override
	public List<Annotation> verify(String bpmnXML, AnalysisInformation info) {
		// Create a list to store the errors.
		List<Annotation> errors = new ArrayList<Annotation>();

		// Create a reader for the BPMN XML string.
		FullBPMNReader reader = new FullBPMNReader(new File(bpmnXML), info);

		// Build the workflow graphs.
		errors.addAll(reader.compute());
		List<WorkflowGraph> graphs = reader.getResult();

		// Analyze each workflow graph.
		if (graphs != null) {
			for (WorkflowGraph g : graphs) {

				// Create a new verifier.
				Verifier verifier = new Verifier(g, Mojo.createMap(g, Mojo.findMax(g)),
						info);

				// Start the time measurement
				info.startTimeMeasurement(g, "Verifier");

				errors.addAll(verifier.compute());

				// Stop the time measurement
				info.endTimeMeasurement(g, "Verifier");
			}
		}

		return errors;
	}

}
