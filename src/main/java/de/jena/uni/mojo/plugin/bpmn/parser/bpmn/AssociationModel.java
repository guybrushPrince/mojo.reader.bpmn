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

import org.activiti.designer.bpmn2.model.AssociationDirection;
import org.activiti.designer.bpmn2.model.Process;

/**
 * An extracted part of the activiti designer bpmn 2.0 model.
 * It defines the Association Model.
 * 
 * @author Dipl.-Inf. Thomas M. Prinz
 * 
 */
public class AssociationModel {

	/**
	 * The id of the association model.
	 */
	public String id;
	
	/**
	 * The direction of the association.
	 */
	public AssociationDirection associationDirection;
	
	/**
	 * The source reference of the association.
	 */
	public String sourceRef;
	
	/**
	 * The target reference of the association.
	 */
	public String targetRef;
	
	/**
	 * The parent process.
	 */
	public Process parentProcess;
}
