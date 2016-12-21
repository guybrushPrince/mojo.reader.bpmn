/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jena.uni.mojo.plugin.bpmn.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.activiti.designer.bpmn2.model.ActivitiListener;
import org.activiti.designer.bpmn2.model.Activity;
import org.activiti.designer.bpmn2.model.Artifact;
import org.activiti.designer.bpmn2.model.AssociationDirection;
import org.activiti.designer.bpmn2.model.BoundaryEvent;
import org.activiti.designer.bpmn2.model.BusinessRuleTask;
import org.activiti.designer.bpmn2.model.CallActivity;
import org.activiti.designer.bpmn2.model.EndEvent;
import org.activiti.designer.bpmn2.model.ErrorEventDefinition;
import org.activiti.designer.bpmn2.model.EventGateway;
import org.activiti.designer.bpmn2.model.EventSubProcess;
import org.activiti.designer.bpmn2.model.ExclusiveGateway;
import org.activiti.designer.bpmn2.model.FieldExtension;
import org.activiti.designer.bpmn2.model.FlowElement;
import org.activiti.designer.bpmn2.model.FlowNode;
import org.activiti.designer.bpmn2.model.FormProperty;
import org.activiti.designer.bpmn2.model.FormValue;
import org.activiti.designer.bpmn2.model.Gateway;
import org.activiti.designer.bpmn2.model.IOParameter;
import org.activiti.designer.bpmn2.model.InclusiveGateway;
import org.activiti.designer.bpmn2.model.IntermediateCatchEvent;
import org.activiti.designer.bpmn2.model.Lane;
import org.activiti.designer.bpmn2.model.MailTask;
import org.activiti.designer.bpmn2.model.ManualTask;
import org.activiti.designer.bpmn2.model.Message;
import org.activiti.designer.bpmn2.model.MessageEventDefinition;
import org.activiti.designer.bpmn2.model.MultiInstanceLoopCharacteristics;
import org.activiti.designer.bpmn2.model.ParallelGateway;
import org.activiti.designer.bpmn2.model.Pool;
import org.activiti.designer.bpmn2.model.Process;
import org.activiti.designer.bpmn2.model.ReceiveTask;
import org.activiti.designer.bpmn2.model.ScriptTask;
import org.activiti.designer.bpmn2.model.ServiceTask;
import org.activiti.designer.bpmn2.model.Signal;
import org.activiti.designer.bpmn2.model.SignalEventDefinition;
import org.activiti.designer.bpmn2.model.StartEvent;
import org.activiti.designer.bpmn2.model.SubProcess;
import org.activiti.designer.bpmn2.model.Task;
import org.activiti.designer.bpmn2.model.TextAnnotation;
import org.activiti.designer.bpmn2.model.ThrowEvent;
import org.activiti.designer.bpmn2.model.TimerEventDefinition;
import org.activiti.designer.bpmn2.model.UserTask;
import org.activiti.designer.bpmn2.model.alfresco.AlfrescoMailTask;
import org.activiti.designer.bpmn2.model.alfresco.AlfrescoScriptTask;

import de.jena.uni.mojo.plugin.bpmn.parser.bpmn.AssociationModel;
import de.jena.uni.mojo.plugin.bpmn.parser.bpmn.BoundaryEventModel;
import de.jena.uni.mojo.plugin.bpmn.parser.bpmn.Bpmn2MemoryModel;
import de.jena.uni.mojo.plugin.bpmn.parser.bpmn.FieldModel;
import de.jena.uni.mojo.plugin.bpmn.parser.bpmn.SequenceFlowModel;

/**
 * Shamelessly taken from the Activiti designer.
 * 
 * @author Tijs Rademakers
 * @author Saeid Mirzaei
 */
public class BpmnParser {

	private static final String ACTIVITI_EXTENSIONS_NAMESPACE = "http://activiti.org/bpmn";
	private static final String CLASS_TYPE = "classType";
	private static final String EXPRESSION_TYPE = "expressionType";
	private static final String DELEGATE_EXPRESSION_TYPE = "delegateExpressionType";
	private static final String ALFRESCO_TYPE = "alfrescoScriptType";

	public boolean bpmdiInfoFound;
	public List<SequenceFlowModel> sequenceFlowList = new ArrayList<SequenceFlowModel>();
	public List<AssociationModel> associationModels = new ArrayList<AssociationModel>();
	private final List<BoundaryEventModel> boundaryList = new ArrayList<BoundaryEventModel>();

	static void parseUsersExpression(final String assignmentText,
			final List<String> users, final List<String> groups) {
		List<String> assignmentList = new ArrayList<String>();
		if (assignmentText.contains(",")) {
			final String[] assignmentArray = assignmentText.split(",");
			assignmentList = Arrays.asList(assignmentArray);
		} else {
			assignmentList.add(assignmentText);
		}
		for (String assignmentValue : assignmentList) {
			if (assignmentValue == null)
				continue;
			assignmentValue = assignmentValue.trim();
			if (assignmentValue.length() == 0)
				continue;

			if (assignmentValue.trim().startsWith("user(")) {
				users.add(assignmentValue.substring("user(".length(),
						assignmentValue.indexOf(')')));

			} else {
				if (assignmentValue.startsWith("group("))
					assignmentValue = assignmentValue.substring(
							"group(".length(), assignmentValue.indexOf(')'));
				groups.add(assignmentValue);
			}
		}

	}

	public void parseBpmn(final XMLStreamReader xtr,
			final Bpmn2MemoryModel model) {
		try {
			boolean processExtensionAvailable = false;
			Process activeProcess = null;
			final List<SubProcess> activeSubProcessList = new ArrayList<SubProcess>();
			while (xtr.hasNext()) {
				try {
					xtr.next();
				} catch (final Exception e) {
					return;
				}

				if (xtr.isEndElement()
						&& "subProcess".equalsIgnoreCase(xtr.getLocalName())) {
					activeSubProcessList
							.remove(activeSubProcessList.size() - 1);
				}

				if (xtr.isStartElement() == false)
					continue;

				if (xtr.isStartElement()
						&& "definitions".equalsIgnoreCase(xtr.getLocalName())) {

					model.setTargetNamespace(xtr.getAttributeValue(null,
							"targetNamespace"));

				} else if (xtr.isStartElement()
						&& "signal".equalsIgnoreCase(xtr.getLocalName())) {

					if (isNotEmpty(xtr
							.getAttributeValue(null, "id"))) {
						final Signal signal = new Signal();
						signal.setId(xtr.getAttributeValue(null, "id"));
						signal.setName(xtr.getAttributeValue(null, "name"));
						model.getSignals().add(signal);
					}

				} else if (xtr.isStartElement()
						&& "message".equalsIgnoreCase(xtr.getLocalName())) {

					if (isNotEmpty(xtr
							.getAttributeValue(null, "id"))) {
						final Message message = new Message();
						message.setId(xtr.getAttributeValue(null, "id"));
						message.setName(xtr.getAttributeValue(null, "name"));
						model.getMessages().add(message);
					}

				} else if (xtr.isStartElement()
						&& "participant".equalsIgnoreCase(xtr.getLocalName())) {

					if (isNotEmpty(xtr
							.getAttributeValue(null, "id"))) {
						final Pool pool = new Pool();
						pool.setId(xtr.getAttributeValue(null, "id"));
						pool.setName(xtr.getAttributeValue(null, "name"));
						pool.setProcessRef(xtr.getAttributeValue(null,
								"processRef"));
						model.getPools().add(pool);
					}

				} else if (xtr.isStartElement()
						&& "process".equalsIgnoreCase(xtr.getLocalName())) {

					if (isNotEmpty(xtr
							.getAttributeValue(null, "id"))) {
						final String processId = xtr.getAttributeValue(null,
								"id");

						processExtensionAvailable = true;
						final Process process = new Process();
						process.setId(processId);
						process.setName(xtr.getAttributeValue(null, "name"));

						// parse candidate starter Users
						if (isNotEmpty(xtr.getAttributeValue(
								ACTIVITI_EXTENSIONS_NAMESPACE,
								"candidateStarterUsers"))) {
							final String expression = xtr.getAttributeValue(
									ACTIVITI_EXTENSIONS_NAMESPACE,
									"candidateStarterUsers");
							String[] expressionList = null;
							if (expression.contains(",")) {
								expressionList = expression.split(",");
							} else {
								expressionList = new String[] { expression
										.trim() };
							}
							for (final String user : expressionList) {
								process.getCandidateStarterUsers().add(
										user.trim());
							}
						}

						// parse candidate starter Groups
						if (isNotEmpty(xtr.getAttributeValue(
								ACTIVITI_EXTENSIONS_NAMESPACE,
								"candidateStarterGroups"))) {
							final String expression = xtr.getAttributeValue(
									ACTIVITI_EXTENSIONS_NAMESPACE,
									"candidateStarterGroups");
							String[] expressionList = null;
							if (expression.contains(",")) {
								expressionList = expression.split(",");
							} else {
								expressionList = new String[] { expression
										.trim() };
							}
							for (final String group : expressionList) {
								process.getCandidateStarterGroups().add(
										group.trim());
							}
						}
						model.getProcesses().add(process);
						activeProcess = process;
					}

				} else if (xtr.isStartElement()
						&& "lane".equalsIgnoreCase(xtr.getLocalName())) {
					final Lane lane = new Lane();
					lane.setId(xtr.getAttributeValue(null, "id"));
					lane.setName(xtr.getAttributeValue(null, "name"));
					lane.setParentProcess(activeProcess);
					activeProcess.getLanes().add(lane);

					while (xtr.hasNext()) {
						xtr.next();
						if (xtr.isStartElement()
								&& "flowNodeRef".equalsIgnoreCase(xtr
										.getLocalName())) {
							lane.getFlowReferences().add(xtr.getElementText());
						} else if (xtr.isEndElement()
								&& "lane".equalsIgnoreCase(xtr.getLocalName())) {
							break;
						}
					}

				} else if (xtr.isStartElement()
						&& "documentation".equalsIgnoreCase(xtr.getLocalName())) {

					final String docText = xtr.getElementText();
					if (isNotEmpty(docText)) {

						if (activeSubProcessList.size() > 0) {
							activeSubProcessList.get(
									activeSubProcessList.size() - 1)
									.setDocumentation(docText);
						} else if (activeProcess != null) {
							activeProcess.setDocumentation(docText);
						}
					}

				} else if (processExtensionAvailable == true
						&& xtr.isStartElement()
						&& "extensionElements".equalsIgnoreCase(xtr
								.getLocalName())) {

					activeProcess.getExecutionListeners().addAll(
							parseListeners(xtr, activeProcess));

					processExtensionAvailable = false;

				} else {

					Artifact currentArtifact = null;
					FlowNode currentActivity = null;
					final String elementId = xtr.getAttributeValue(null, "id");
					final String elementName = xtr.getAttributeValue(null,
							"name");
					final boolean async = parseAsync(xtr);
					final boolean notExclusive = parseNotExclusive(xtr);
					final String defaultFlow = xtr.getAttributeValue(null,
							"default");
					processExtensionAvailable = false;

					if (xtr.isStartElement()
							&& "startEvent"
									.equalsIgnoreCase(xtr.getLocalName())) {
						currentActivity = parseStartEvent(xtr);

					} else if (xtr.isStartElement()
							&& "subProcess"
									.equalsIgnoreCase(xtr.getLocalName())) {
						currentActivity = parseSubProcess(xtr);
						activeSubProcessList.add((SubProcess) currentActivity);

					} else if (activeSubProcessList.size() > 0
							&& xtr.isStartElement()
							&& "extensionElements".equalsIgnoreCase(xtr
									.getLocalName())) {
						activeSubProcessList
								.get(activeSubProcessList.size() - 1)
								.getExecutionListeners()
								.addAll(parseListeners(xtr, null));

					} else if (activeSubProcessList.size() > 0
							&& xtr.isStartElement()
							&& "multiInstanceLoopCharacteristics"
									.equalsIgnoreCase(xtr.getLocalName())) {

						activeSubProcessList.get(
								activeSubProcessList.size() - 1)
								.setLoopCharacteristics(
										parseMultiInstanceDef(xtr));

					} else if (xtr.isStartElement()
							&& "userTask".equalsIgnoreCase(xtr.getLocalName())) {
						currentActivity = parseUserTask(xtr);

					} else if (xtr.isStartElement()
							&& "serviceTask".equalsIgnoreCase(xtr
									.getLocalName())) {

						if ("mail".equalsIgnoreCase(xtr.getAttributeValue(
								ACTIVITI_EXTENSIONS_NAMESPACE, "type"))) {
							currentActivity = parseMailTask(xtr, "serviceTask");
						} else if ("org.alfresco.repo.workflow.activiti.script.AlfrescoScriptDelegate"
								.equalsIgnoreCase(xtr.getAttributeValue(
										ACTIVITI_EXTENSIONS_NAMESPACE, "class"))) {
							currentActivity = parseAlfrescoScriptTask(xtr);
						} else {
							currentActivity = parseServiceTask(xtr);
						}

					} else if (xtr.isStartElement()
							&& "sendTask".equalsIgnoreCase(xtr.getLocalName())) {

						currentActivity = parseMailTask(xtr, "sendTask");

					} else if (xtr.isStartElement()
							&& "task".equalsIgnoreCase(xtr.getLocalName())) {
						currentActivity = parseTask(xtr);

					} else if (xtr.isStartElement()
							&& "scriptTask"
									.equalsIgnoreCase(xtr.getLocalName())) {
						currentActivity = parseScriptTask(xtr);

					} else if (xtr.isStartElement()
							&& "manualTask"
									.equalsIgnoreCase(xtr.getLocalName())) {
						currentActivity = parseManualTask(xtr);

					} else if (xtr.isStartElement()
							&& "receiveTask".equalsIgnoreCase(xtr
									.getLocalName())) {
						currentActivity = parseReceiveTask(xtr);

					} else if (xtr.isStartElement()
							&& "businessRuleTask".equalsIgnoreCase(xtr
									.getLocalName())) {
						currentActivity = parseBusinessRuleTask(xtr);

					} else if (xtr.isStartElement()
							&& "callActivity".equalsIgnoreCase(xtr
									.getLocalName())) {
						currentActivity = parseCallActivity(xtr);

					} else if (xtr.isStartElement()
							&& "endEvent".equalsIgnoreCase(xtr.getLocalName())) {
						currentActivity = parseEndEvent(xtr);

					} else if (xtr.isStartElement()
							&& "intermediateCatchEvent".equalsIgnoreCase(xtr
									.getLocalName())) {
						currentActivity = parseIntermediateCatchEvent(xtr);

					} else if (xtr.isStartElement()
							&& "intermediateThrowEvent".equalsIgnoreCase(xtr
									.getLocalName())) {
						currentActivity = parseIntermediateThrowEvent(xtr);

					} else if (xtr.isStartElement()
							&& "exclusiveGateway".equalsIgnoreCase(xtr
									.getLocalName())) {
						currentActivity = parseExclusiveGateway(xtr);

					} else if (xtr.isStartElement()
							&& "inclusiveGateway".equalsIgnoreCase(xtr
									.getLocalName())) {
						currentActivity = parseInclusiveGateway(xtr);

					} else if (xtr.isStartElement()
							&& "parallelGateway".equalsIgnoreCase(xtr
									.getLocalName())) {
						currentActivity = parseParallelGateway(xtr);

					} else if (xtr.isStartElement()
							&& "eventBasedGateway".equalsIgnoreCase(xtr
									.getLocalName())) {
						currentActivity = parseEventGateway(xtr);

					} else if (xtr.isStartElement()
							&& "boundaryEvent".equalsIgnoreCase(xtr
									.getLocalName())) {
						final String elementid = xtr.getAttributeValue(null,
								"id");
						final BoundaryEventModel event = parseBoundaryEvent(xtr);
						event.boundaryEvent.setId(elementid);
						event.parentProcess = activeProcess;
						boundaryList.add(event);

					} else if (xtr.isStartElement()
							&& "sequenceFlow".equalsIgnoreCase(xtr
									.getLocalName())) {
						final SequenceFlowModel sequenceFlow = parseSequenceFlow(xtr);
						sequenceFlow.parentProcess = activeProcess;
						sequenceFlowList.add(sequenceFlow);

					} else if (xtr.isStartElement()
							&& "textAnnotation".equalsIgnoreCase(xtr
									.getLocalName())) {
						currentArtifact = parseTextAnnotation(xtr);

					} else if (xtr.isStartElement()
							&& "association".equalsIgnoreCase(xtr
									.getLocalName())) {
						final AssociationModel associationModel = parseAssociation(xtr);
						associationModel.parentProcess = activeProcess;
						associationModels.add(associationModel);

					} else if (xtr.isStartElement()
							&& "BPMNShape".equalsIgnoreCase(xtr.getLocalName())) {
						bpmdiInfoFound = true;
						/** @author Thomas Prinz removed */
						/*final String id = */xtr.getAttributeValue(null,
								"bpmnElement");
						while (xtr.hasNext()) {
							xtr.next();
							if (xtr.isStartElement()
									&& "Bounds".equalsIgnoreCase(xtr
											.getLocalName())) {
								
								break;
							}
						}

					} else if (xtr.isStartElement()
							&& "BPMNEdge".equalsIgnoreCase(xtr.getLocalName())) {
						/** @author Thomas Prinz removed */
						/*final String id = */xtr.getAttributeValue(null,
								"bpmnElement");

						while (xtr.hasNext()) {
							xtr.next();
							if (xtr.isStartElement()
									&& "BPMNLabel".equalsIgnoreCase(xtr
											.getLocalName())) {

								while (xtr.hasNext()) {
									xtr.next();
									if (xtr.isStartElement()
											&& "Bounds".equalsIgnoreCase(xtr
													.getLocalName())) {
										break;
									} else if (xtr.isEndElement()
											&& "BPMNLabel".equalsIgnoreCase(xtr
													.getLocalName())) {
										break;
									}
								}

							} else if (xtr.isStartElement()
									&& "waypoint".equalsIgnoreCase(xtr
											.getLocalName())) {

							} else if (xtr.isEndElement()
									&& "BPMNEdge".equalsIgnoreCase(xtr
											.getLocalName())) {
								break;
							}
						}
					}

					if (currentArtifact != null) {
						currentArtifact.setId(elementId);

						if (isInSubProcess(activeSubProcessList)) {
							final SubProcess currentSubProcess = activeSubProcessList
									.get(activeSubProcessList.size() - 2);
							currentSubProcess.getArtifacts().add(
									currentArtifact);

						} else {
							activeProcess.getArtifacts().add(currentArtifact);
						}
					}

					if (currentActivity != null) {

						currentActivity.setId(elementId);
						currentActivity.setName(elementName);

						if (currentActivity instanceof Activity) {

							final Activity activity = (Activity) currentActivity;
							activity.setAsynchronous(async);
							activity.setNotExclusive(notExclusive);
							if (isNotEmpty(defaultFlow)) {
								activity.setDefaultFlow(defaultFlow);
							}
						}

						if (currentActivity instanceof Gateway) {
							if (isNotEmpty(defaultFlow)) {
								((Gateway) currentActivity)
										.setDefaultFlow(defaultFlow);
							}
						}

						if (currentActivity instanceof SubProcess) {
							if (isInSubProcess(activeSubProcessList)) {
								activeSubProcessList
										.get(activeSubProcessList.size() - 2)
										.getFlowElements().add(currentActivity);

							} else {
								activeProcess.getFlowElements().add(
										currentActivity);
							}

						} else if (activeSubProcessList.size() > 0) {
							activeSubProcessList
									.get(activeSubProcessList.size() - 1)
									.getFlowElements().add(currentActivity);
						} else {
							activeProcess.getFlowElements()
									.add(currentActivity);
						}
					}
				}
			}

			for (final BoundaryEventModel boundaryModel : boundaryList) {
				final FlowNode flowNode = getFlowNode(
						boundaryModel.attachedRef,
						boundaryModel.parentProcess.getFlowElements());
				if (flowNode != null) {
					boundaryModel.boundaryEvent
							.setAttachedToRef((Activity) flowNode);
					((Activity) flowNode).getBoundaryEvents().add(
							boundaryModel.boundaryEvent);
				}
			}

		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	private boolean isInSubProcess(final List<SubProcess> subProcessList) {
		if (subProcessList.size() > 1) {
			return true;
		} else {
			return false;
		}
	}

	private FlowNode getFlowNode(final String elementid,
			final List<FlowElement> elementList) {
		FlowNode flowNode = null;
		for (final FlowElement flowElement : elementList) {
			if (flowElement.getId().equalsIgnoreCase(elementid)) {
				flowNode = (FlowNode) flowElement;
				break;
			}

			if (flowElement instanceof SubProcess) {
				flowNode = getFlowNode(elementid,
						((SubProcess) flowElement).getFlowElements());
				if (flowNode != null) {
					break;
				}
			}
		}
		return flowNode;
	}

	private StartEvent parseStartEvent(final XMLStreamReader xtr) {
		StartEvent startEvent = null;
		if (isNotEmpty(xtr.getAttributeValue(
				ACTIVITI_EXTENSIONS_NAMESPACE, "formKey"))) {
		}
		if (startEvent == null) {
			startEvent = new StartEvent();
		}

		if (isNotEmpty(xtr.getAttributeValue(
				ACTIVITI_EXTENSIONS_NAMESPACE, "initiator"))) {
			startEvent.setInitiator(xtr.getAttributeValue(
					ACTIVITI_EXTENSIONS_NAMESPACE, "initiator"));
		}

		if (isNotEmpty(xtr.getAttributeValue(
				ACTIVITI_EXTENSIONS_NAMESPACE, "formKey"))) {
			startEvent.setFormKey(xtr.getAttributeValue(
					ACTIVITI_EXTENSIONS_NAMESPACE, "formKey"));
		}
		boolean readyWithStartEvent = false;
		try {
			while (readyWithStartEvent == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "formProperty".equalsIgnoreCase(xtr.getLocalName())) {
					final FormProperty property = new FormProperty();
					startEvent.getFormProperties().add(property);
					parseFormProperty(property, xtr);

				} else if (xtr.isStartElement()
						&& "errorEventDefinition".equalsIgnoreCase(xtr
								.getLocalName())) {
					startEvent.getEventDefinitions().add(
							parseErrorEventDefinition(xtr));

				} else if (xtr.isStartElement()
						&& "timerEventDefinition".equalsIgnoreCase(xtr
								.getLocalName())) {
					startEvent.getEventDefinitions().add(
							parseTimerEventDefinition(xtr));

				} else if (xtr.isStartElement()
						&& "messageEventDefinition".equalsIgnoreCase(xtr
								.getLocalName())) {
					final MessageEventDefinition messageDefinition = new MessageEventDefinition();
					messageDefinition.setMessageRef(xtr.getAttributeValue(null,
							"messageRef"));
					startEvent.getEventDefinitions().add(messageDefinition);
				} else if (xtr.isStartElement()
						&& "documentation".equalsIgnoreCase(xtr.getLocalName())) {
					/** @author Norbert Spiess load documentation */
					final String docText = xtr.getElementText();
					if (isEmpty(docText) == false) {
						startEvent.setDocumentation(docText);
					}
				} else if (xtr.isEndElement()
						&& "startEvent".equalsIgnoreCase(xtr.getLocalName())) {
					readyWithStartEvent = true;
				}
			}
		} catch (final Exception e) {
		}
		return startEvent;
	}

	private void parseFormProperty(final FormProperty property,
			final XMLStreamReader xtr) {
		if (isNotEmpty(xtr.getAttributeValue(null, "id"))) {
			property.setId(xtr.getAttributeValue(null, "id"));
		}
		if (isNotEmpty(xtr.getAttributeValue(null, "name"))) {
			property.setName(xtr.getAttributeValue(null, "name"));
		}
		if (isNotEmpty(xtr.getAttributeValue(null, "type"))) {
			property.setType(xtr.getAttributeValue(null, "type"));
		}
		if (isNotEmpty(xtr.getAttributeValue(null, "value"))) {
			property.setValue(xtr.getAttributeValue(null, "value"));
		}
		if (isNotEmpty(xtr.getAttributeValue(null, "variable"))) {
			property.setVariable(xtr.getAttributeValue(null, "variable"));
		}
		if (isNotEmpty(xtr.getAttributeValue(null, "expression"))) {
			property.setExpression(xtr.getAttributeValue(null, "expression"));
		}
		if (isNotEmpty(xtr.getAttributeValue(null, "default"))) {
			property.setDefaultExpression(xtr
					.getAttributeValue(null, "default"));
		}
		if (isNotEmpty(xtr.getAttributeValue(null, "datePattern"))) {
			property.setDatePattern(xtr.getAttributeValue(null, "datePattern"));
		}
		if (isNotEmpty(xtr.getAttributeValue(null, "required"))) {
			property.setRequired(Boolean.valueOf(xtr.getAttributeValue(null,
					"required")));
		}
		if (isNotEmpty(xtr.getAttributeValue(null, "readable"))) {
			property.setReadable(Boolean.valueOf(xtr.getAttributeValue(null,
					"readable")));
		}
		if (isNotEmpty(xtr.getAttributeValue(null, "writable"))) {
			property.setWriteable(Boolean.valueOf(xtr.getAttributeValue(null,
					"writable")));
		}

		boolean readyWithFormProperty = false;
		try {
			while (readyWithFormProperty == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "value".equalsIgnoreCase(xtr.getLocalName())) {
					final FormValue value = new FormValue();
					value.setId(xtr.getAttributeValue(null, "id"));
					value.setName(xtr.getAttributeValue(null, "name"));
					property.getFormValues().add(value);

				} else if (xtr.isEndElement()
						&& "formProperty".equalsIgnoreCase(xtr.getLocalName())) {
					readyWithFormProperty = true;
				}
			}
		} catch (final Exception e) {
		}
	}

	private MultiInstanceLoopCharacteristics parseMultiInstanceDef(
			final XMLStreamReader xtr) {
		final MultiInstanceLoopCharacteristics multiInstanceDef = new MultiInstanceLoopCharacteristics();

		if (xtr.getAttributeValue(null, "isSequential") != null) {
			multiInstanceDef.setSequential(Boolean.valueOf(xtr
					.getAttributeValue(null, "isSequential")));
		}

		if (xtr.getAttributeValue(ACTIVITI_EXTENSIONS_NAMESPACE, "collection") != null) {
			multiInstanceDef.setInputDataItem(xtr.getAttributeValue(
					ACTIVITI_EXTENSIONS_NAMESPACE, "collection"));
		}
		if (xtr.getAttributeValue(ACTIVITI_EXTENSIONS_NAMESPACE,
				"elementVariable") != null) {
			multiInstanceDef.setElementVariable(xtr.getAttributeValue(
					ACTIVITI_EXTENSIONS_NAMESPACE, "elementVariable"));
		}

		boolean readyWithMultiInstance = false;
		try {
			while (readyWithMultiInstance == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "loopCardinality".equalsIgnoreCase(xtr
								.getLocalName())) {
					multiInstanceDef.setLoopCardinality(xtr.getElementText());

				} else if (xtr.isStartElement()
						&& "loopDataInputRef".equalsIgnoreCase(xtr
								.getLocalName())) {
					multiInstanceDef.setInputDataItem(xtr.getElementText());

				} else if (xtr.isStartElement()
						&& "inputDataItem".equalsIgnoreCase(xtr.getLocalName())) {
					if (xtr.getAttributeValue(null, "name") != null) {
						multiInstanceDef.setElementVariable(xtr
								.getAttributeValue(null, "name"));
					}

				} else if (xtr.isStartElement()
						&& "completionCondition".equalsIgnoreCase(xtr
								.getLocalName())) {
					multiInstanceDef.setCompletionCondition(xtr
							.getElementText());

				} else if (xtr.isEndElement()
						&& "multiInstanceLoopCharacteristics"
								.equalsIgnoreCase(xtr.getLocalName())) {
					readyWithMultiInstance = true;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return multiInstanceDef;
	}

	private EndEvent parseEndEvent(final XMLStreamReader xtr) {
		final EndEvent endEvent = new EndEvent();

		boolean readyWithEndEvent = false;
		try {
			while (readyWithEndEvent == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "errorEventDefinition".equalsIgnoreCase(xtr
								.getLocalName())) {
					endEvent.getEventDefinitions().add(
							parseErrorEventDefinition(xtr));
				} else if (xtr.isStartElement()
						&& "documentation".equalsIgnoreCase(xtr.getLocalName())) {
					/** @author Norbert Spiess load documentation */
					final String docText = xtr.getElementText();
					if (isEmpty(docText) == false) {
						endEvent.setDocumentation(docText);
					}
				} else if (xtr.isEndElement()
						&& "endEvent".equalsIgnoreCase(xtr.getLocalName())) {
					readyWithEndEvent = true;
				}
			}
		} catch (final Exception e) {
		}
		return endEvent;
	}

	private SubProcess parseSubProcess(final XMLStreamReader xtr) {
		SubProcess subProcess = null;
		if (isNotEmpty(xtr.getAttributeValue(null,
				"triggeredByEvent"))
				&& "true".equalsIgnoreCase(xtr.getAttributeValue(null,
						"triggeredByEvent"))) {

			subProcess = new EventSubProcess();
		} else {
			subProcess = new SubProcess();
		}
		return subProcess;
	}

	private ExclusiveGateway parseExclusiveGateway(final XMLStreamReader xtr) {
		final ExclusiveGateway exclusiveGateway = new ExclusiveGateway();
		/** @author Norbert Spiess load documentation */
		boolean readyWithExclusiveGateway = false;
		try {
			while (readyWithExclusiveGateway == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "documentation".equalsIgnoreCase(xtr.getLocalName())) {

					final String docText = xtr.getElementText();
					if (isEmpty(docText) == false) {
						exclusiveGateway.setDocumentation(docText);
					}
				} else if (xtr.isEndElement()
						&& "exclusiveGateway".equalsIgnoreCase(xtr
								.getLocalName())) {
					readyWithExclusiveGateway = true;
				}
			}
		} catch (final Exception e) {
		}
		return exclusiveGateway;
	}

	private InclusiveGateway parseInclusiveGateway(final XMLStreamReader xtr) {
		final InclusiveGateway inclusiveGateway = new InclusiveGateway();
		/** @author Norbert Spiess load documentation */
		boolean readyWithInclusiveGateway = false;
		try {
			while (readyWithInclusiveGateway == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "documentation".equalsIgnoreCase(xtr.getLocalName())) {

					final String docText = xtr.getElementText();
					if (isEmpty(docText) == false) {
						inclusiveGateway.setDocumentation(docText);
					}
				} else if (xtr.isEndElement()
						&& "inclusiveGateway".equalsIgnoreCase(xtr
								.getLocalName())) {
					readyWithInclusiveGateway = true;
				}
			}
		} catch (final Exception e) {
		}
		return inclusiveGateway;
	}

	private ParallelGateway parseParallelGateway(final XMLStreamReader xtr) {
		final ParallelGateway parallelGateway = new ParallelGateway();
		/** @author Norbert Spiess load documentation */
		boolean readyWithParallelGateway = false;
		try {
			while (readyWithParallelGateway == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "documentation".equalsIgnoreCase(xtr.getLocalName())) {

					final String docText = xtr.getElementText();
					if (isEmpty(docText) == false) {
						parallelGateway.setDocumentation(docText);
					}
				} else if (xtr.isEndElement()
						&& "parallelGateway".equalsIgnoreCase(xtr
								.getLocalName())) {
					readyWithParallelGateway = true;
				}
			}
		} catch (final Exception e) {
		}
		return parallelGateway;
	}

	private EventGateway parseEventGateway(final XMLStreamReader xtr) {
		final EventGateway eventGateway = new EventGateway();
		return eventGateway;
	}

	private SequenceFlowModel parseSequenceFlow(final XMLStreamReader xtr) {
		final SequenceFlowModel sequenceFlow = new SequenceFlowModel();
		sequenceFlow.sourceRef = xtr.getAttributeValue(null, "sourceRef");
		sequenceFlow.targetRef = xtr.getAttributeValue(null, "targetRef");
		sequenceFlow.id = xtr.getAttributeValue(null, "id");
		sequenceFlow.name = xtr.getAttributeValue(null, "name");
		sequenceFlow.conditionExpression = parseSequenceFlowCondition(xtr,
				sequenceFlow);
		/** @author Thomas Prinz load documentation */
		boolean readyWithSequenceFlow = false;
		try {
			System.out.println(xtr.getText());
			while (!readyWithSequenceFlow && xtr.hasNext()) {
				System.out.println(xtr.getText());
				xtr.next();
				if (xtr.isStartElement()
						&& "documentation".equalsIgnoreCase(xtr.getLocalName())) {

					final String docText = xtr.getElementText();
					if (isEmpty(docText) == false) {
						sequenceFlow.conditionExpression = docText;
					}
				} else if (xtr.isEndElement()
						&& "sequenceFlow".equalsIgnoreCase(xtr.getLocalName())) {
					readyWithSequenceFlow = true;
				}
			}
		} catch (final Exception e) {

		}
		return sequenceFlow;
	}

	private AssociationModel parseAssociation(final XMLStreamReader xtr) {

		final AssociationModel association = new AssociationModel();
		association.id = xtr.getAttributeValue(null, "id");
		association.sourceRef = xtr.getAttributeValue(null, "sourceRef");
		association.targetRef = xtr.getAttributeValue(null, "targetRef");

		final String direction = xtr.getAttributeValue(null,
				"associationDirection");
		for (final AssociationDirection oneDir : AssociationDirection.values()) {
			if (oneDir.getValue().equalsIgnoreCase(direction)) {
				association.associationDirection = oneDir;
			}
		}

		return association;
	}

	private TextAnnotation parseTextAnnotation(final XMLStreamReader xtr)
			throws XMLStreamException {
		final TextAnnotation ta = new TextAnnotation();
		ta.setId(xtr.getAttributeValue(null, "id"));
		ta.setTextFormat(xtr.getAttributeValue(null, "textFormat"));
		while (xtr.hasNext()) {
			xtr.next();
			if (xtr.isStartElement()
					&& "text".equalsIgnoreCase(xtr.getLocalName())) {
				ta.setText(xtr.getElementText());
				break;
			}
		}
		return ta;
	}

	private static String parseSequenceFlowCondition(final XMLStreamReader xtr,
			final SequenceFlowModel sequenceFlow) {
		String condition = null;
		if (xtr.getAttributeValue(null, "name") != null
				&& xtr.getAttributeValue(null, "name").contains("${")) {
			condition = xtr.getAttributeValue(null, "name");
		}
		boolean readyWithSequenceFlow = false;
		try {
			while (readyWithSequenceFlow == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "conditionExpression".equalsIgnoreCase(xtr
								.getLocalName())) {
					condition = xtr.getElementText();

				} else if (xtr.isStartElement()
						&& "extensionElements".equalsIgnoreCase(xtr
								.getLocalName())) {
					sequenceFlow.listenerList.addAll(parseListeners(xtr, null));

				} else if (xtr.isEndElement()
						&& "sequenceFlow".equalsIgnoreCase(xtr.getLocalName())) {
					readyWithSequenceFlow = true;
				}
			}
		} catch (final Exception e) {
		}
		return condition;
	}

	private UserTask parseUserTask(final XMLStreamReader xtr) {
		UserTask userTask = null;
		if (xtr.getAttributeValue(ACTIVITI_EXTENSIONS_NAMESPACE, "formKey") != null) {
		}
		if (userTask == null) {
			userTask = new UserTask();
		}

		if (isNotEmpty(xtr.getAttributeValue(
				ACTIVITI_EXTENSIONS_NAMESPACE, "dueDate"))) {
			userTask.setDueDate(xtr.getAttributeValue(
					ACTIVITI_EXTENSIONS_NAMESPACE, "dueDate"));
		}

		if (isNotEmpty(xtr.getAttributeValue(
				ACTIVITI_EXTENSIONS_NAMESPACE, "assignee"))) {
			final String assignee = xtr.getAttributeValue(
					ACTIVITI_EXTENSIONS_NAMESPACE, "assignee");
			userTask.setAssignee(assignee);

		}

		if (isNotEmpty(xtr.getAttributeValue(
				ACTIVITI_EXTENSIONS_NAMESPACE, "candidateUsers"))) {
			final String expression = xtr.getAttributeValue(
					ACTIVITI_EXTENSIONS_NAMESPACE, "candidateUsers");
			String[] expressionList = null;
			if (expression.contains(";")) {
				expressionList = expression.split(";");
			} else {
				expressionList = new String[] { expression };
			}
			for (final String user : expressionList) {
				userTask.getCandidateUsers().add(user);
			}

		}

		if (isNotEmpty(xtr.getAttributeValue(
				ACTIVITI_EXTENSIONS_NAMESPACE, "candidateGroups"))) {
			final String expression = xtr.getAttributeValue(
					ACTIVITI_EXTENSIONS_NAMESPACE, "candidateGroups");
			String[] expressionList = null;
			if (expression.contains(";")) {
				expressionList = expression.split(";");
			} else {
				expressionList = new String[] { expression };
			}
			for (final String group : expressionList) {
				userTask.getCandidateGroups().add(group);
			}
		}

		if (isNotEmpty(xtr.getAttributeValue(
				ACTIVITI_EXTENSIONS_NAMESPACE, "formKey"))) {
			userTask.setFormKey(xtr.getAttributeValue(
					ACTIVITI_EXTENSIONS_NAMESPACE, "formKey"));
		}

		if (isNotEmpty(xtr.getAttributeValue(
				ACTIVITI_EXTENSIONS_NAMESPACE, "priority"))) {
			Integer priorityValue = null;
			try {
				priorityValue = Integer.valueOf(xtr.getAttributeValue(
						ACTIVITI_EXTENSIONS_NAMESPACE, "priority"));
			} catch (final Exception e) {
			}
			userTask.setPriority(priorityValue + "");
		}

		boolean readyWithUserTask = false;
		try {
			String assignmentType = null;
			ActivitiListener listener = null;
			while (readyWithUserTask == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "humanPerformer"
								.equalsIgnoreCase(xtr.getLocalName())) {
					assignmentType = "humanPerformer";

				} else if (xtr.isStartElement()
						&& "potentialOwner"
								.equalsIgnoreCase(xtr.getLocalName())) {
					assignmentType = "potentialOwner";

				} else if (xtr.isStartElement()
						&& "formalExpression".equalsIgnoreCase(xtr
								.getLocalName())) {
					if ("potentialOwner".equals(assignmentType)) {

						final ArrayList<String> users = new ArrayList<String>();
						final ArrayList<String> groups = new ArrayList<String>();
						final String assignmentText = xtr.getElementText();

						parseUsersExpression(assignmentText, users, groups);

						for (final String user : users)
							userTask.getCandidateUsers().add(user);
						for (final String group : groups)
							userTask.getCandidateGroups().add(group);

					} else {
						userTask.setAssignee(xtr.getElementText());
					}

				} else if (xtr.isStartElement()
						&& ("taskListener".equalsIgnoreCase(xtr.getLocalName()))) {

					if (xtr.getAttributeValue(null, "class") != null
							&& "org.alfresco.repo.workflow.activiti.listener.ScriptExecutionListener"
									.equals(xtr
											.getAttributeValue(null, "class"))
							|| "org.alfresco.repo.workflow.activiti.tasklistener.ScriptTaskListener"
									.equals(xtr
											.getAttributeValue(null, "class"))) {

						listener = new ActivitiListener();
						listener.setEvent(xtr.getAttributeValue(null, "event"));
						listener.setImplementationType(ALFRESCO_TYPE);
						boolean readyWithAlfrescoType = false;
						while (readyWithAlfrescoType == false && xtr.hasNext()) {
							xtr.next();
							if (xtr.isStartElement()
									&& "field".equalsIgnoreCase(xtr
											.getLocalName())) {
								final String script = getFieldExtensionValue(xtr);
								if (script != null && script.length() > 0) {
									listener.setImplementation(script);
								}
								readyWithAlfrescoType = true;
							} else if (xtr.isEndElement()
									&& "extensionElements".equalsIgnoreCase(xtr
											.getLocalName())) {
								readyWithAlfrescoType = true;
								readyWithUserTask = true;
							}
						}
					} else {
						listener = parseListener(xtr);
					}
					userTask.getTaskListeners().add(listener);

				} else if (xtr.isStartElement()
						&& "field".equalsIgnoreCase(xtr.getLocalName())) {
					listener.getFieldExtensions().add(parseFieldExtension(xtr));

				} else if (xtr.isStartElement()
						&& "formProperty".equalsIgnoreCase(xtr.getLocalName())) {
					final FormProperty property = new FormProperty();
					userTask.getFormProperties().add(property);
					parseFormProperty(property, xtr);

				} else if (xtr.isStartElement()
						&& "documentation".equalsIgnoreCase(xtr.getLocalName())) {

					final String docText = xtr.getElementText();
					if (isEmpty(docText) == false) {
						userTask.setDocumentation(docText);
					}

				} else if (xtr.isStartElement()
						&& "multiInstanceLoopCharacteristics"
								.equalsIgnoreCase(xtr.getLocalName())) {
					userTask.setLoopCharacteristics(parseMultiInstanceDef(xtr));

				} else if (xtr.isEndElement()
						&& "userTask".equalsIgnoreCase(xtr.getLocalName())) {
					readyWithUserTask = true;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return userTask;
	}

	private ScriptTask parseScriptTask(final XMLStreamReader xtr) {
		final ScriptTask scriptTask = new ScriptTask();
		scriptTask.setScriptFormat(xtr.getAttributeValue(null, "scriptFormat"));
		boolean readyWithScriptTask = false;
		try {
			while (readyWithScriptTask == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "script".equalsIgnoreCase(xtr.getLocalName())) {
					scriptTask.setScript(xtr.getElementText());

				} else if (xtr.isStartElement()
						&& "extensionElements".equalsIgnoreCase(xtr
								.getLocalName())) {
					scriptTask.getExecutionListeners().addAll(
							parseListeners(xtr, null));

				} else if (xtr.isStartElement()
						&& "multiInstanceLoopCharacteristics"
								.equalsIgnoreCase(xtr.getLocalName())) {
					scriptTask
							.setLoopCharacteristics(parseMultiInstanceDef(xtr));
				} else if (xtr.isStartElement()
						&& "documentation".equalsIgnoreCase(xtr.getLocalName())) {
					/** @author Norbert Spiess load documentation */
					final String docText = xtr.getElementText();
					if (isEmpty(docText) == false) {
						scriptTask.setDocumentation(docText);
					}
				} else if (xtr.isEndElement()
						&& "scriptTask".equalsIgnoreCase(xtr.getLocalName())) {
					readyWithScriptTask = true;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return scriptTask;
	}

	private MailTask parseMailTask(final XMLStreamReader xtr,
			final String taskType) {
		final MailTask mailTask = new MailTask();
		boolean readyWithMailTask = false;
		try {
			while (readyWithMailTask == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "extensionElements".equalsIgnoreCase(xtr
								.getLocalName())) {
					fillExtensionsForMailTask(xtr, mailTask);

				} else if (xtr.isStartElement()
						&& "multiInstanceLoopCharacteristics"
								.equalsIgnoreCase(xtr.getLocalName())) {
					mailTask.setLoopCharacteristics(parseMultiInstanceDef(xtr));
				} else if (xtr.isStartElement()
						&& "documentation".equalsIgnoreCase(xtr.getLocalName())) {
					/** @author Norbert Spiess load documentation */
					final String docText = xtr.getElementText();
					if (isEmpty(docText) == false) {
						mailTask.setDocumentation(docText);
					}
				} else if (xtr.isEndElement()
						&& taskType.equalsIgnoreCase(xtr.getLocalName())) {
					readyWithMailTask = true;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return mailTask;
	}

	private Task parseAlfrescoScriptTask(final XMLStreamReader xtr) {
		List<FieldModel> fieldList = new ArrayList<FieldModel>();
		boolean readyWithExtensions = false;
		ActivitiListener listener = null;
		Task task = null;
		MultiInstanceLoopCharacteristics multiInstanceDef = null;
		final List<ActivitiListener> listenerList = new ArrayList<ActivitiListener>();
		try {
			while (readyWithExtensions == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "field".equalsIgnoreCase(xtr.getLocalName())) {
					final FieldModel field = parseFieldModel(xtr);
					fieldList.add(field);

				} else if (xtr.isStartElement()
						&& "executionListener".equalsIgnoreCase(xtr
								.getLocalName())) {
					if (fieldList.size() > 0) {
						task = fillAlfrescoScriptTaskElements(fieldList);
						fieldList = new ArrayList<FieldModel>();
					}
					listener = parseListener(xtr);
					listenerList.add(listener);

				} else if (xtr.isEndElement()
						&& "executionListener".equalsIgnoreCase(xtr
								.getLocalName())) {
					if (fieldList.size() > 0) {
						fillListenerWithFields(listener, fieldList);
						fieldList = new ArrayList<FieldModel>();
					}

				} else if (xtr.isEndElement()
						&& "extensionElements".equalsIgnoreCase(xtr
								.getLocalName())) {
					if (fieldList.size() > 0) {
						task = fillAlfrescoScriptTaskElements(fieldList);
					}

				} else if (xtr.isStartElement()
						&& "multiInstanceLoopCharacteristics"
								.equalsIgnoreCase(xtr.getLocalName())) {
					multiInstanceDef = parseMultiInstanceDef(xtr);

				} else if (xtr.isStartElement()
						&& "documentation".equalsIgnoreCase(xtr.getLocalName())) {
					/** @author Norbert Spiess load documentation */
					final String docText = xtr.getElementText();
					if (isEmpty(docText) == false) {
						task.setDocumentation(docText);
					}
				} else if (xtr.isEndElement()
						&& "serviceTask".equalsIgnoreCase(xtr.getLocalName())) {
					readyWithExtensions = true;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}

		if (task == null) {
			return null;
		}

		if (multiInstanceDef != null) {
			task.setLoopCharacteristics(multiInstanceDef);
		}

		if (listenerList.size() > 0) {
			task.getExecutionListeners().addAll(listenerList);
		}

		return task;
	}

	private static FieldModel parseFieldModel(final XMLStreamReader xtr) {
		final FieldModel field = new FieldModel();
		field.name = xtr.getAttributeValue(null, "name");
		field.value = getFieldExtensionValue(xtr);
		return field;
	}

	private Task fillAlfrescoScriptTaskElements(final List<FieldModel> fieldList) {
		if (fieldList == null || fieldList.size() == 0)
			return null;
		boolean isMailScript = false;
		String mailScript = null;
		for (final FieldModel field : fieldList) {
			if ("script".equalsIgnoreCase(field.name)
					&& isMailScript(field.value)) {
				isMailScript = true;
				mailScript = field.value;
			}
		}
		Task task = null;

		if (isMailScript == true) {
			final AlfrescoMailTask mailTask = new AlfrescoMailTask();
			String value = getMailParamValue(mailScript, "mail.parameters.to");
			if (isNotEmpty(value)) {
				mailTask.setTo(value);
			}
			value = getMailParamValue(mailScript, "mail.parameters.to_many");
			if (isNotEmpty(value)) {
				mailTask.setToMany(value);
			}
			value = getMailParamValue(mailScript, "mail.parameters.subject");
			if (isNotEmpty(value)) {
				mailTask.setSubject(value);
			}
			value = getMailParamValue(mailScript, "mail.parameters.from");
			if (isNotEmpty(value)) {
				mailTask.setFrom(value);
			}
			value = getMailParamValue(mailScript, "mail.parameters.template");
			if (isNotEmpty(value)) {
				mailTask.setTemplate(value);
			}
			value = getMailParamValue(mailScript,
					"mail.parameters.template_model");
			if (isNotEmpty(value)) {
				mailTask.setTemplateModel(value);
			}
			value = getMailParamValue(mailScript, "mail.parameters.text");
			if (isNotEmpty(value)) {
				mailTask.setText(value);
			}
			value = getMailParamValue(mailScript, "mail.parameters.html");
			if (isNotEmpty(value)) {
				mailTask.setHtml(value);
			}
			task = mailTask;

		} else {

			final AlfrescoScriptTask scriptTask = new AlfrescoScriptTask();
			for (final FieldModel field : fieldList) {
				if ("script".equalsIgnoreCase(field.name)) {
					scriptTask.setScript(field.value);
				} else if ("runAs".equalsIgnoreCase(field.name)) {
					scriptTask.setRunAs(field.value);
				} else if ("scriptProcessor".equalsIgnoreCase(field.name)) {
					scriptTask.setScriptProcessor(field.value);
				}
			}
			task = scriptTask;
		}
		return task;
	}

	private String getMailParamValue(final String mailScript,
			final String searchString) {
		final int index = mailScript.indexOf(searchString);
		if (index > -1) {
			final int startIndex = mailScript.indexOf("=", index);
			final int endIndex = mailScript.indexOf(";", index);
			return mailScript.substring(startIndex + 1, endIndex).trim();
		} else {
			return null;
		}
	}

	private boolean isMailScript(final String script) {
		boolean isMailScript = false;
		if (script != null) {
			if (script.contains("var mail = actions.create(\"mail\");")
					&& script.contains("mail.execute(bpm_package);")) {

				isMailScript = true;
			}
		}
		return isMailScript;
	}

	private void fillListenerWithFields(final ActivitiListener listener,
			final List<FieldModel> fieldList) {
		if (fieldList == null || fieldList.size() == 0)
			return;
		for (final FieldModel field : fieldList) {
			final FieldExtension extension = new FieldExtension();
			extension.setFieldName(field.name);
			extension.setExpression(field.value);
			listener.getFieldExtensions().add(extension);
		}
	}

	private ServiceTask parseServiceTask(final XMLStreamReader xtr) {
		final ServiceTask serviceTask = new ServiceTask();
		if (xtr.getAttributeValue(ACTIVITI_EXTENSIONS_NAMESPACE, "class") != null) {
			serviceTask.setImplementationType(CLASS_TYPE);
			serviceTask.setImplementation(xtr.getAttributeValue(
					ACTIVITI_EXTENSIONS_NAMESPACE, "class"));

		} else if (xtr.getAttributeValue(ACTIVITI_EXTENSIONS_NAMESPACE,
				"expression") != null) {
			serviceTask.setImplementationType(EXPRESSION_TYPE);
			serviceTask.setImplementation(xtr.getAttributeValue(
					ACTIVITI_EXTENSIONS_NAMESPACE, "expression"));
		} else if (xtr.getAttributeValue(ACTIVITI_EXTENSIONS_NAMESPACE,
				"delegateExpression") != null) {
			serviceTask.setImplementationType(DELEGATE_EXPRESSION_TYPE);
			serviceTask.setImplementation(xtr.getAttributeValue(
					ACTIVITI_EXTENSIONS_NAMESPACE, "delegateExpression"));
		}

		if (xtr.getAttributeValue(ACTIVITI_EXTENSIONS_NAMESPACE,
				"resultVariableName") != null) {
			serviceTask.setResultVariableName(xtr.getAttributeValue(
					ACTIVITI_EXTENSIONS_NAMESPACE, "resultVariableName"));
		}

		boolean readyWithServiceTask = false;
		try {
			while (readyWithServiceTask == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "extensionElements".equalsIgnoreCase(xtr
								.getLocalName())) {
					fillExtensionsForServiceTask(xtr, serviceTask);

				} else if (xtr.isStartElement()
						&& "multiInstanceLoopCharacteristics"
								.equalsIgnoreCase(xtr.getLocalName())) {
					serviceTask
							.setLoopCharacteristics(parseMultiInstanceDef(xtr));

				} else if (xtr.isStartElement()
						&& "documentation".equalsIgnoreCase(xtr.getLocalName())) {

					final String docText = xtr.getElementText();
					if (isEmpty(docText) == false) {
						serviceTask.setDocumentation(docText);
					}

				} else if (xtr.isStartElement()
						&& "documentation".equalsIgnoreCase(xtr.getLocalName())) {
					/** @author Norbert Spiess load documentation */
					final String docText = xtr.getElementText();
					if (isEmpty(docText) == false) {
						serviceTask.setDocumentation(docText);
					}
				} else if (xtr.isEndElement()
						&& "serviceTask".equalsIgnoreCase(xtr.getLocalName())) {
					readyWithServiceTask = true;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return serviceTask;
	}

	private ServiceTask parseTask(final XMLStreamReader xtr) {
		final ServiceTask serviceTask = new ServiceTask();
		return serviceTask;
	}

	private static void fillExtensionsForServiceTask(final XMLStreamReader xtr,
			final ServiceTask serviceTask) {
		List<FieldExtension> extensionList = new ArrayList<FieldExtension>();
		boolean readyWithExtensions = false;
		try {
			ActivitiListener listener = null;
			while (readyWithExtensions == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "field".equalsIgnoreCase(xtr.getLocalName())) {
					final FieldExtension extension = parseFieldExtension(xtr);
					extensionList.add(extension);

				} else if (xtr.isStartElement()
						&& "executionListener".equalsIgnoreCase(xtr
								.getLocalName())) {
					if (extensionList.size() > 0) {
						serviceTask.getFieldExtensions().addAll(extensionList);
						extensionList = new ArrayList<FieldExtension>();
					}
					listener = parseListener(xtr);
					serviceTask.getExecutionListeners().add(listener);

				} else if (xtr.isEndElement()
						&& "executionListener".equalsIgnoreCase(xtr
								.getLocalName())) {
					if (extensionList.size() > 0) {
						listener.getFieldExtensions().addAll(extensionList);
						extensionList = new ArrayList<FieldExtension>();
					}

				} else if (xtr.isEndElement()
						&& "extensionElements".equalsIgnoreCase(xtr
								.getLocalName())) {
					if (extensionList.size() > 0) {
						serviceTask.getFieldExtensions().addAll(extensionList);
					}
					readyWithExtensions = true;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	private static void fillExtensionsForMailTask(final XMLStreamReader xtr,
			final MailTask mailTask) {
		List<FieldExtension> extensionList = new ArrayList<FieldExtension>();
		boolean readyWithExtensions = false;
		try {
			ActivitiListener listener = null;
			while (readyWithExtensions == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "field".equalsIgnoreCase(xtr.getLocalName())) {
					final String name = xtr.getAttributeValue(null, "name");
					if ("to".equalsIgnoreCase(name)) {
						mailTask.setTo(getFieldExtensionValue(xtr));
					} else if ("from".equalsIgnoreCase(name)) {
						mailTask.setFrom(getFieldExtensionValue(xtr));
					} else if ("cc".equalsIgnoreCase(name)) {
						mailTask.setCc(getFieldExtensionValue(xtr));
					} else if ("bcc".equalsIgnoreCase(name)) {
						mailTask.setBcc(getFieldExtensionValue(xtr));
					} else if ("charset".equalsIgnoreCase(name)) {
						mailTask.setCharset(getFieldExtensionValue(xtr));
					} else if ("subject".equalsIgnoreCase(name)) {
						mailTask.setSubject(getFieldExtensionValue(xtr));
					} else if ("html".equalsIgnoreCase(name)) {
						mailTask.setHtml(getFieldExtensionValue(xtr));
					} else if ("text".equalsIgnoreCase(name)) {
						mailTask.setText(getFieldExtensionValue(xtr));
					}
				} else if (xtr.isStartElement()
						&& "field".equalsIgnoreCase(xtr.getLocalName())) {
					final FieldExtension extension = parseFieldExtension(xtr);
					extensionList.add(extension);

				} else if (xtr.isStartElement()
						&& "executionListener".equalsIgnoreCase(xtr
								.getLocalName())) {
					listener = parseListener(xtr);
					mailTask.getExecutionListeners().add(listener);

				} else if (xtr.isEndElement()
						&& "executionListener".equalsIgnoreCase(xtr
								.getLocalName())) {
					if (extensionList.size() > 0) {
						listener.getFieldExtensions().addAll(extensionList);
						extensionList = new ArrayList<FieldExtension>();
					}
				} else if (xtr.isEndElement()
						&& "extensionElements".equalsIgnoreCase(xtr
								.getLocalName())) {
					readyWithExtensions = true;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	private static void fillExtensionsForCallActivity(
			final XMLStreamReader xtr, final CallActivity callActivity) {
		List<FieldExtension> extensionList = new ArrayList<FieldExtension>();
		boolean readyWithExtensions = false;
		try {
			ActivitiListener listener = null;
			while (readyWithExtensions == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "field".equalsIgnoreCase(xtr.getLocalName())) {
					final FieldExtension extension = parseFieldExtension(xtr);
					extensionList.add(extension);

				} else if (xtr.isStartElement()
						&& "executionListener".equalsIgnoreCase(xtr
								.getLocalName())) {
					listener = parseListener(xtr);
					callActivity.getExecutionListeners().add(listener);

				} else if (xtr.isEndElement()
						&& "executionListener".equalsIgnoreCase(xtr
								.getLocalName())) {
					if (extensionList.size() > 0) {
						listener.getFieldExtensions().addAll(extensionList);
						extensionList = new ArrayList<FieldExtension>();
					}

				} else if (xtr.isStartElement()
						&& "in".equalsIgnoreCase(xtr.getLocalName())) {
					final String source = xtr.getAttributeValue(null, "source");
					final String sourceExpression = xtr.getAttributeValue(null,
							"sourceExpression");
					final String target = xtr.getAttributeValue(null, "target");
					final String targetExpression = xtr.getAttributeValue(null,
							"targetExpression");
					if ((isNotEmpty(source) || 
							isNotEmpty(sourceExpression))
							&& (isNotEmpty(target) || 
									isNotEmpty(targetExpression))) {

						final IOParameter parameter = new IOParameter();
						if (isNotEmpty(sourceExpression)) {
							parameter.setSourceExpression(sourceExpression);
						} else {
							parameter.setSource(source);
						}

						if (isNotEmpty(targetExpression)) {
							parameter.setTargetExpression(targetExpression);
						} else {
							parameter.setTarget(target);
						}
						callActivity.getInParameters().add(parameter);
					}

				} else if (xtr.isStartElement()
						&& "out".equalsIgnoreCase(xtr.getLocalName())) {
					final String source = xtr.getAttributeValue(null, "source");
					final String sourceExpression = xtr.getAttributeValue(null,
							"sourceExpression");
					final String target = xtr.getAttributeValue(null, "target");
					final String targetExpression = xtr.getAttributeValue(null,
							"targetExpression");
					if ((isNotEmpty(source) || 
							isNotEmpty(sourceExpression))
							&& (isNotEmpty(target) || 
									isNotEmpty(targetExpression))) {

						final IOParameter parameter = new IOParameter();
						if (isNotEmpty(sourceExpression)) {
							parameter.setSourceExpression(sourceExpression);
						} else {
							parameter.setSource(source);
						}

						if (isNotEmpty(targetExpression)) {
							parameter.setTargetExpression(targetExpression);
						} else {
							parameter.setTarget(target);
						}
						callActivity.getOutParameters().add(parameter);
					}

				} else if (xtr.isEndElement()
						&& "extensionElements".equalsIgnoreCase(xtr
								.getLocalName())) {
					readyWithExtensions = true;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	private static FieldExtension parseFieldExtension(final XMLStreamReader xtr) {
		final FieldExtension extension = new FieldExtension();
		extension.setFieldName(xtr.getAttributeValue(null, "name"));
		extension.setExpression(getFieldExtensionValue(xtr));
		return extension;
	}

	private static String getFieldExtensionValue(final XMLStreamReader xtr) {
		if (xtr.getAttributeValue(null, "stringValue") != null) {
			return xtr.getAttributeValue(null, "stringValue");

		} else if (xtr.getAttributeValue(null, "expression") != null) {
			return xtr.getAttributeValue(null, "expression");

		} else {
			/** @author Thomas Prinz removed */
			//final boolean readyWithFieldExtension = false;
			try {
				while (/*readyWithFieldExtension == false && */xtr.hasNext()) {
					xtr.next();
					if (xtr.isStartElement()
							&& "string".equalsIgnoreCase(xtr.getLocalName())) {
						return xtr.getElementText().trim();

					} else if (xtr.isStartElement()
							&& "expression"
									.equalsIgnoreCase(xtr.getLocalName())) {
						return xtr.getElementText().trim();

					} else if (xtr.isEndElement()
							&& "field".equalsIgnoreCase(xtr.getLocalName())) {
						return null;
					}
				}
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	// model == null means no need to parse userExpression
	private static List<ActivitiListener> parseListeners(
			final XMLStreamReader xtr, final Process activeProcess) {
		final List<ActivitiListener> listenerList = new ArrayList<ActivitiListener>();
		boolean readyWithListener = false;
		boolean inPotentialStarter = false;
		boolean inResourceAssignmentExpression = false;
		boolean inFormalExpression = false;
		String formalExpression = "";

		try {
			ActivitiListener listener = null;
			while (readyWithListener == false && xtr.hasNext()) {
				xtr.next();

				if (inFormalExpression && activeProcess != null) {
					formalExpression = xtr.getText();
					final List<String> users = new ArrayList<String>();
					final List<String> groups = new ArrayList<String>();

					parseUsersExpression(formalExpression, users, groups);

					for (final String user : users) {
						activeProcess.getCandidateStarterUsers().add(user);

					}

					for (final String group : groups) {
						activeProcess.getCandidateStarterGroups().add(group);
					}

					inFormalExpression = false;
				}

				if (xtr.isStartElement()
						&& ("executionListener".equalsIgnoreCase(xtr
								.getLocalName()) || "taskListener"
								.equalsIgnoreCase(xtr.getLocalName()))) {

					if (xtr.getAttributeValue(null, "class") != null
							&& "org.alfresco.repo.workflow.activiti.listener.ScriptExecutionListener"
									.equals(xtr
											.getAttributeValue(null, "class"))
							|| "org.alfresco.repo.workflow.activiti.tasklistener.ScriptTaskListener"
									.equals(xtr
											.getAttributeValue(null, "class"))) {

						listener = new ActivitiListener();
						listener.setEvent(xtr.getAttributeValue(null, "event"));
						listener.setImplementationType(ALFRESCO_TYPE);
						boolean readyWithAlfrescoType = false;
						while (readyWithAlfrescoType == false && xtr.hasNext()) {
							xtr.next();
							if (xtr.isStartElement()
									&& "field".equalsIgnoreCase(xtr
											.getLocalName())) {
								final String script = getFieldExtensionValue(xtr);
								if (script != null && script.length() > 0) {
									listener.setImplementation(script);
								}
								readyWithAlfrescoType = true;
							} else if (xtr.isEndElement()
									&& "extensionElements".equalsIgnoreCase(xtr
											.getLocalName())) {
								readyWithAlfrescoType = true;
								readyWithListener = true;
							}
						}
					} else {
						listener = parseListener(xtr);
					}
					listenerList.add(listener);

				} else if (xtr.isStartElement()
						&& "field".equalsIgnoreCase(xtr.getLocalName())) {
					listener.getFieldExtensions().add(parseFieldExtension(xtr));
				} else if (xtr.isStartElement()
						&& "potentialStarter".equalsIgnoreCase(xtr
								.getLocalName())) {
					inPotentialStarter = true;

				} else if (inPotentialStarter
						&& xtr.isStartElement()
						&& "resourceAssignmentExpression".equalsIgnoreCase(xtr
								.getLocalName())) {
					inResourceAssignmentExpression = true;
				} else if (inPotentialStarter
						&& xtr.isEndElement()
						&& "resourceAssignmentExpression".equalsIgnoreCase(xtr
								.getLocalName())) {
					inResourceAssignmentExpression = false;
				} else if (inResourceAssignmentExpression
						&& xtr.isStartElement()
						&& "formalExpression".equalsIgnoreCase(xtr
								.getLocalName())) {
					inFormalExpression = true;

				} else if (xtr.isEndElement()
						&& "potentialStarter".equalsIgnoreCase(xtr
								.getLocalName())) {
					inPotentialStarter = false;
				} else if (xtr.isEndElement()
						&& "extensionElements".equalsIgnoreCase(xtr
								.getLocalName())) {
					readyWithListener = true;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return listenerList;
	}

	private static ActivitiListener parseListener(final XMLStreamReader xtr) {
		final ActivitiListener listener = new ActivitiListener();
		if (isNotEmpty(xtr.getAttributeValue(null, "class"))) {
			listener.setImplementation(xtr.getAttributeValue(null, "class"));
			listener.setImplementationType(CLASS_TYPE);
		} else if (isNotEmpty(xtr.getAttributeValue(null,
				"expression"))) {
			listener.setImplementation(xtr
					.getAttributeValue(null, "expression"));
			listener.setImplementationType(EXPRESSION_TYPE);
		} else if (isNotEmpty(xtr.getAttributeValue(null,
				"delegateExpression"))) {
			listener.setImplementation(xtr.getAttributeValue(null,
					"delegateExpression"));
			listener.setImplementationType(DELEGATE_EXPRESSION_TYPE);
		}
		listener.setEvent(xtr.getAttributeValue(null, "event"));
		return listener;
	}

	private ManualTask parseManualTask(final XMLStreamReader xtr) {
		final ManualTask manualTask = new ManualTask();
		boolean readyWithTask = false;
		try {
			while (readyWithTask == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "extensionElements".equalsIgnoreCase(xtr
								.getLocalName())) {
					manualTask.getExecutionListeners().addAll(
							parseListeners(xtr, null));

				} else if (xtr.isStartElement()
						&& "multiInstanceLoopCharacteristics"
								.equalsIgnoreCase(xtr.getLocalName())) {
					manualTask
							.setLoopCharacteristics(parseMultiInstanceDef(xtr));

				} else if (xtr.isStartElement()
						&& "documentation".equalsIgnoreCase(xtr.getLocalName())) {
					/** @author Norbert Spiess load documentation */
					final String docText = xtr.getElementText();
					if (isEmpty(docText) == false) {
						manualTask.setDocumentation(docText);
					}
				} else if (xtr.isEndElement()
						&& "manualTask".equalsIgnoreCase(xtr.getLocalName())) {
					readyWithTask = true;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return manualTask;
	}

	private CallActivity parseCallActivity(final XMLStreamReader xtr) {
		final CallActivity callActivity = new CallActivity();
		if (xtr.getAttributeValue(null, "calledElement") != null
				&& xtr.getAttributeValue(null, "calledElement").length() > 0) {
			callActivity.setCalledElement(xtr.getAttributeValue(null,
					"calledElement"));
		}
		boolean readyWithTask = false;
		try {
			while (readyWithTask == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "extensionElements".equalsIgnoreCase(xtr
								.getLocalName())) {

					fillExtensionsForCallActivity(xtr, callActivity);

				} else if (xtr.isStartElement()
						&& "multiInstanceLoopCharacteristics"
								.equalsIgnoreCase(xtr.getLocalName())) {
					callActivity
							.setLoopCharacteristics(parseMultiInstanceDef(xtr));

				} else if (xtr.isStartElement()
						&& "documentation".equalsIgnoreCase(xtr.getLocalName())) {
					/** @author Norbert Spiess load documentation */
					final String docText = xtr.getElementText();
					if (isEmpty(docText) == false) {
						callActivity.setDocumentation(docText);
					}

				} else if (xtr.isEndElement()
						&& "callActivity".equalsIgnoreCase(xtr.getLocalName())) {
					readyWithTask = true;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return callActivity;
	}

	private ReceiveTask parseReceiveTask(final XMLStreamReader xtr) {
		final ReceiveTask receiveTask = new ReceiveTask();
		boolean readyWithTask = false;
		try {
			while (readyWithTask == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "extensionElements".equalsIgnoreCase(xtr
								.getLocalName())) {
					receiveTask.getExecutionListeners().addAll(
							parseListeners(xtr, null));

				} else if (xtr.isStartElement()
						&& "multiInstanceLoopCharacteristics"
								.equalsIgnoreCase(xtr.getLocalName())) {
					receiveTask
							.setLoopCharacteristics(parseMultiInstanceDef(xtr));

				} else if (xtr.isStartElement()
						&& "documentation".equalsIgnoreCase(xtr.getLocalName())) {
					/** @author Norbert Spiess load documentation */
					final String docText = xtr.getElementText();
					if (isEmpty(docText) == false) {
						receiveTask.setDocumentation(docText);
					}
				} else if (xtr.isEndElement()
						&& "receiveTask".equalsIgnoreCase(xtr.getLocalName())) {
					readyWithTask = true;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return receiveTask;
	}

	private BusinessRuleTask parseBusinessRuleTask(final XMLStreamReader xtr) {
		final BusinessRuleTask businessRuleTask = new BusinessRuleTask();
		if (xtr.getAttributeValue(ACTIVITI_EXTENSIONS_NAMESPACE, "rules") != null) {
			final String ruleNames = xtr.getAttributeValue(
					ACTIVITI_EXTENSIONS_NAMESPACE, "rules");
			if (ruleNames != null && ruleNames.length() > 0) {
				businessRuleTask.getRuleNames().clear();
				if (ruleNames.contains(",") == false) {
					businessRuleTask.getRuleNames().add(ruleNames);
				} else {
					final String[] ruleNameList = ruleNames.split(",");
					for (final String rule : ruleNameList) {
						businessRuleTask.getRuleNames().add(rule);
					}
				}
			}
		}
		if (xtr.getAttributeValue(ACTIVITI_EXTENSIONS_NAMESPACE,
				"ruleVariablesInput") != null) {
			final String inputNames = xtr.getAttributeValue(
					ACTIVITI_EXTENSIONS_NAMESPACE, "ruleVariablesInput");
			if (inputNames != null && inputNames.length() > 0) {
				businessRuleTask.getInputVariables().clear();
				if (inputNames.contains(",") == false) {
					businessRuleTask.getInputVariables().add(inputNames);
				} else {
					final String[] inputNamesList = inputNames.split(",");
					for (final String input : inputNamesList) {
						businessRuleTask.getInputVariables().add(input);
					}
				}
			}
		}
		if (xtr.getAttributeValue(ACTIVITI_EXTENSIONS_NAMESPACE, "exclude") != null) {
			businessRuleTask.setExclude(Boolean.valueOf(xtr.getAttributeValue(
					ACTIVITI_EXTENSIONS_NAMESPACE, "exclude")));
		}
		if (xtr.getAttributeValue(ACTIVITI_EXTENSIONS_NAMESPACE,
				"resultVariableName") != null) {
			businessRuleTask.setResultVariableName(xtr.getAttributeValue(
					ACTIVITI_EXTENSIONS_NAMESPACE, "resultVariableName"));
		}

		boolean readyWithTask = false;
		try {
			while (readyWithTask == false && xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "extensionElements".equalsIgnoreCase(xtr
								.getLocalName())) {
					businessRuleTask.getExecutionListeners().addAll(
							parseListeners(xtr, null));

				} else if (xtr.isStartElement()
						&& "multiInstanceLoopCharacteristics"
								.equalsIgnoreCase(xtr.getLocalName())) {
					businessRuleTask
							.setLoopCharacteristics(parseMultiInstanceDef(xtr));

				} else if (xtr.isStartElement()
						&& "documentation".equalsIgnoreCase(xtr.getLocalName())) {
					/** @author Norbert Spiess load documentation */
					final String docText = xtr.getElementText();
					if (isEmpty(docText) == false) {
						businessRuleTask.setDocumentation(docText);
					}
				} else if (xtr.isEndElement()
						&& "businessRuleTask".equalsIgnoreCase(xtr
								.getLocalName())) {
					readyWithTask = true;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return businessRuleTask;
	}

	private BoundaryEventModel parseBoundaryEvent(final XMLStreamReader xtr) {
		final BoundaryEvent boundaryEvent = new BoundaryEvent();
		boundaryEvent.setName(xtr.getAttributeValue(null, "name"));

		if (xtr.getAttributeValue(null, "cancelActivity") != null) {
			final String cancelActivity = xtr.getAttributeValue(null,
					"cancelActivity");
			if ("true".equalsIgnoreCase(cancelActivity)) {
				boundaryEvent.setCancelActivity(true);
			} else {
				boundaryEvent.setCancelActivity(false);
			}
		}

		final BoundaryEventModel model = new BoundaryEventModel();
		model.boundaryEvent = boundaryEvent;
		model.attachedRef = xtr.getAttributeValue(null, "attachedToRef");
		try {
			while (xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "timerEventDefinition".equalsIgnoreCase(xtr
								.getLocalName())) {
					model.type = BoundaryEventModel.TIMEEVENT;
					boundaryEvent.getEventDefinitions().add(
							parseTimerEventDefinition(xtr));
					break;

				} else if (xtr.isStartElement()
						&& "errorEventDefinition".equalsIgnoreCase(xtr
								.getLocalName())) {
					model.type = BoundaryEventModel.ERROREVENT;
					boundaryEvent.getEventDefinitions().add(
							parseErrorEventDefinition(xtr));
					break;

				} else if (xtr.isStartElement()
						&& "signalEventDefinition".equalsIgnoreCase(xtr
								.getLocalName())) {
					model.type = BoundaryEventModel.SIGNALEVENT;
					boundaryEvent.getEventDefinitions().add(
							parseSignalEventDefinition(xtr));
					break;

				} else if (xtr.isEndElement()
						&& "boundaryEvent".equalsIgnoreCase(xtr.getLocalName())) {
					break;
				}
			}

		} catch (final Exception e) {
			e.printStackTrace();
		}
		return model;
	}

	private boolean parseAsync(final XMLStreamReader xtr) {
		boolean async = false;
		final String asyncString = xtr.getAttributeValue(
				ACTIVITI_EXTENSIONS_NAMESPACE, "async");
		if ("true".equalsIgnoreCase(asyncString)) {
			async = true;
		}
		return async;
	}

	private boolean parseNotExclusive(final XMLStreamReader xtr) {
		boolean notExclusive = false;
		final String exclusiveString = xtr.getAttributeValue(
				ACTIVITI_EXTENSIONS_NAMESPACE, "exclusive");
		if ("false".equalsIgnoreCase(exclusiveString)) {
			notExclusive = true;
		}
		return notExclusive;
	}

	private IntermediateCatchEvent parseIntermediateCatchEvent(
			final XMLStreamReader xtr) {
		final IntermediateCatchEvent catchEvent = new IntermediateCatchEvent();

		try {
			while (xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "timerEventDefinition".equalsIgnoreCase(xtr
								.getLocalName())) {
					catchEvent.getEventDefinitions().add(
							parseTimerEventDefinition(xtr));
					break;

				} else if (xtr.isStartElement()
						&& "signalEventDefinition".equalsIgnoreCase(xtr
								.getLocalName())) {
					catchEvent.getEventDefinitions().add(
							parseSignalEventDefinition(xtr));
					break;

				} else if (xtr.isEndElement()
						&& "intermediateCatchEvent".equalsIgnoreCase(xtr
								.getLocalName())) {
					break;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return catchEvent;
	}

	private ThrowEvent parseIntermediateThrowEvent(final XMLStreamReader xtr) {
		final ThrowEvent throwEvent = new ThrowEvent();
		try {
			while (xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "signalEventDefinition".equalsIgnoreCase(xtr
								.getLocalName())) {
					throwEvent.getEventDefinitions().add(
							parseSignalEventDefinition(xtr));
					break;

				} else if (xtr.isEndElement()
						&& "intermediateThrowEvent".equalsIgnoreCase(xtr
								.getLocalName())) {
					break;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return throwEvent;
	}

	private TimerEventDefinition parseTimerEventDefinition(
			final XMLStreamReader xtr) {
		final TimerEventDefinition eventDefinition = new TimerEventDefinition();
		try {
			while (xtr.hasNext()) {
				xtr.next();
				if (xtr.isStartElement()
						&& "timeDuration".equalsIgnoreCase(xtr.getLocalName())) {
					eventDefinition.setTimeDuration(xtr.getElementText());
					break;

				} else if (xtr.isStartElement()
						&& "timeDate".equalsIgnoreCase(xtr.getLocalName())) {
					eventDefinition.setTimeDate(xtr.getElementText());
					break;

				} else if (xtr.isStartElement()
						&& "timeCycle".equalsIgnoreCase(xtr.getLocalName())) {
					eventDefinition.setTimeCycle(xtr.getElementText());
					break;

				} else if (xtr.isEndElement()
						&& "timerEventDefinition".equalsIgnoreCase(xtr
								.getLocalName())) {
					break;
				}
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return eventDefinition;
	}

	private ErrorEventDefinition parseErrorEventDefinition(
			final XMLStreamReader xtr) {
		final ErrorEventDefinition eventDefinition = new ErrorEventDefinition();
		if (isNotEmpty(xtr.getAttributeValue(null, "errorRef"))) {
			eventDefinition.setErrorCode(xtr
					.getAttributeValue(null, "errorRef"));
		}
		return eventDefinition;
	}

	private SignalEventDefinition parseSignalEventDefinition(
			final XMLStreamReader xtr) {
		final SignalEventDefinition eventDefinition = new SignalEventDefinition();
		if (isNotEmpty(xtr.getAttributeValue(null, "signalRef"))) {
			eventDefinition.setSignalRef(xtr.getAttributeValue(null,
					"signalRef"));
		}
		return eventDefinition;
	}
	
	private static boolean isNotEmpty(String str) {
		if (str == null) return false;
		if (!str.isEmpty()) {
			return true;
		} else {
			if (str != "") {
				return true;
			}
		}
		return false;
	}
	
	private static boolean isEmpty(String str) {
		if (str == null) return true;
		if (str.isEmpty()) return true;
		if (str == "") return true;
		return false;
	}
}
