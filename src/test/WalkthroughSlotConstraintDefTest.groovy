package test

import groovy.util.GroovyTestCase

import org.openehr.am.archetype.constraintmodel.ArchetypeSlot
import org.openehr.am.archetype.ontology.OntologyDefinitions
import org.openehr.am.archetype.ontology.QueryBindingItem
import org.openehr.am.archetype.ontology.TermBindingItem
import org.openehr.am.serialize.ADLSerializer

import com.cabolabs.openehr.tools.flattener.ArchetypeManager;
import com.cabolabs.openehr.tools.flattener.ArchetypeWalkthrough

class WalkthroughSlotConstraintDefTest extends GroovyTestCase {

   /*
    * Carga arquetipo sin slots, lo recorre, y deberia dejar el mismo arquetipo con los mismos codigos porque no tiene slots.
    */
   void testWalkWithClosure()
   {
      def f = new File(".")
      println "Current folder: "+ f.getCanonicalPath()
      
      
      // Load archetype
      def loader = ArchetypeManager.getInstance("./src/test/archetypes")
      def archetype = loader.getArchetype("openEHR-EHR-COMPOSITION.slot_constraint_definition.v1")
      

      def walk = new ArchetypeWalkthrough()
      
      
      // =====================================================
      // Observer CComplexObject
      // parchetype es el arquetipo padre del node
      // 
      walk.observeCCO { node, parchetype, walkt, parentNode ->

         println "CCO: "+ parchetype.archetypeId.value + node.path() +" "+ 
                          node.rmTypeName +" "+ node.nodeId
         
         // sobreescribe nodeID
         if (node.nodeID)
         {
            def newCode = walkt.codeMapping.transformCode(parchetype.archetypeId.value, node.nodeID)
            node.setNodeId( newCode )
         }
      }
      
      
      // =====================================================
      // Slot
      walk.observeAS { node, parchetype, walkt, parentNode ->
      
         println "Slot: "+ parchetype.archetypeId.value + node.path() +" "+
                           node.rmTypeName +" "+ node.nodeId
       
         // NO SOBREESCRIBE NODEID PORQUE EL SLOT ES REMOVIDO DEL ARQUETIPO PARA PONER EL REFERENCIADO
       
         // NULL parent: tengo que pasar el parent por parametro
         // https://github.com/openEHR/java-libs/issues/1
         //println "parent:" + node.parent // null
         //println "parent:" + parentNode // CMultipleAttribute
       
         // Quita el slot del attr padre, aqui se agregan las definition de los arquetipos referenciados.
         parentNode.children.remove(node)
       
         // ============================
         // recorre la ontologia y elimina terminos para el nodeId removido.
         walkt.removeTermsForSlot(node)
         
         // ============================
       
         // get referenced archetypes
         def aman = ArchetypeManager.getInstance("./src/test/archetypes")
         
         node.includes.each{ assertion ->
            
            //println assertion.expression // archetype_id/value matches {/openEHR-EHR-INSTRUCTION\.test_ordenes\.v1/}
            //println assertion.expression.leftOperand  // archetype_id/value
            //println assertion.expression.operator     // matches
            //println assertion.expression.rightOperand // /openEHR-EHR-INSTRUCTION\.test_ordenes\.v1/
            
            // regex
            def pattern = assertion.expression.rightOperand.item.pattern
            def archetypes = aman.getArchetypes(node.rmTypeName, pattern)
            
            archetypes.each { ref_archetype ->
               
               // openEHR-EHR-INSTRUCTION.test_ordenes.v1
               println "RefArchetype: "+ ref_archetype.archetypeId.value 
               
               // Actualiza nodeIDs del arquetipo referenciado
               walkt.wt(ref_archetype.definition, null, ref_archetype)
            
               //println "mapping"+ walkt.codeMapping.atCodesMapping
               
               // Procesar ontologia del arquetipo referenciado
               // - modifica codigos y mergea items con el root archetype
               walkt.wt(ref_archetype.ontology, null, ref_archetype)
               
               // Vincula arquetipo referenciado en el lugar del slot del arquetipo parchetype
               parentNode.children.add( ref_archetype.definition )
            }
         }
      } // observe slot
      
      /**
       * list es ontology.termDefinitionsList u 
       * ontology.constraintDefinitionList de ArchetypeOntology
       * list puede ser vacia.
       */
      walk.observeOntDefs { list, parchetype, walkt, attr ->
         
         //println "observeOntDefs: $attr"
         //println "1. OntologyDefinitions: "+ parchetype.archetypeId
         //println " - list: "+ list

         list.each { ontdefs ->
            
            //println "ontdefs.language: "+ ontdefs.language
            
            ontdefs.definitions.each { archetypeTerm ->
               
               //println archetypeTerm.code
               //println " === pido codigo por: "+ parchetype.archetypeId.value +" "+ archetypeTerm.code
               
               // ------------------------------------------------------------------------------------------
               // Codigo seteado en el mapeo del CComplexObject
               def mappedCode = walkt.codeMapping.getMappedCode(parchetype.archetypeId.value, archetypeTerm.code)
               
               println " --- mapped code "+ mappedCode
               
               if (mappedCode) archetypeTerm.code = mappedCode
               
               
               // Todo esto debe pasar si el arquetipo actual no es el root
               if (parchetype != walkt.root)
               {
                  // ------------------------------------------------------------------------------------------
                  // Agrega archetypeTerm al OntlogyDEfinitions con el mismo lenguaje que el
                  // OD que estoy recorriendo pero solo si es un arquetipo referenciado.
                  
                  //println "Distintos: "+ parchetype.archetypeId +" "+ walkt.root.archetypeId
                  //println "Agrega term en: "+ attr
                  //println walkt.root.ontology."$attr"
                  
                  // instanceof List<OntologyDefinitions>
                  def rootOntDefsList = walkt.root.ontology."$attr" // Puede ser una lista vacia
                  
                  //println "rootOntDefsList: "+ rootOntDefsList
                  
                  def ontDefSameLang = rootOntDefsList.find { it.language == ontdefs.language }
                  
                  
                  // si attr es constraintDefinitionsList, el root puede no tener ningun
                  // OntoDefs en attr y el ref_archetype si, sino constraintDefinition debe
                  // crear el OntoDefs en root y meter el archetypeTerm adentro.
                  if (!ontDefSameLang)
                  {
                     if (attr == 'constraintDefinitionsList')
                     {
                        ontDefSameLang = new OntologyDefinitions(ontdefs.language, [])
                        walkt.root.ontology."$attr" << ontDefSameLang
                        
                        // *****************************************************************************
                        // Para constraintDefinitio el codigo de constraint acNNNN ya es sobreescrito
                        // arriba. Lo que falta es que si hay un constraintBinding con ese code,
                        // tambien se sobreescriba.
                        println "constraint code: "+ archetypeTerm.code
                        // *****************************************************************************
                     }
                     else // Para termDefinitionsList si se debe 
                     {
                        // Hay un lenguaje en el arquetipo referenciado que no esta definido en el arquetipo root
                        // Debe traducir el arquetipo root a ese lenguaje para moder hacer el flatten o debe sacar
                        // la traduccion del arquetipo referenciado.
                        if (!ontDefSameLang) throw new Exception("root archetype and referenced archetype have different translation languages")
                     }
                  }
                  
                  //println "Agrega archetypeTerm: "+ archetypeTerm.code
                  ontDefSameLang.definitions << archetypeTerm
               }
            }
         }
      }
   
      walk.observeOntBind { list, parchetype, walkt, attr ->
         
         //println "OntologyBinding: "+ list.class +" "+ attr
         
         // Agrega OntologyBindings al root desde el arquetipo actual (distinto del root).
         if (parchetype != walkt.root)
         {
            def changeItems = [:]
            list.each { ontoBinding ->
               
               // Sobreescribe codigos acNNNN si fueron definidos
               ontoBinding.bindingList.each { ontoBindingItem ->
                  
                  def mappedCode = walkt.codeMapping.getMappedCode(parchetype.archetypeId.value, ontoBindingItem.code)
                  
                  println " --- observeOntBind mapped code for "+ ontoBindingItem.code +" is "+ mappedCode
                  
                  if (mappedCode)
                  {
                     //ontoBindingItem.setCode( mappedCode ) // No me deja setear el codigo, tengo que crear nuevas instancias.
                     def newOntoBindingItem
                     if (ontoBindingItem instanceof TermBindingItem) newOntoBindingItem = new TermBindingItem(mappedCode, ontoBindingItem.terms)
                     else if (ontoBindingItem instanceof QueryBindingItem) newOntoBindingItem = new QueryBindingItem(mappedCode, ontoBindingItem.query)
                     else throw new Exception("No deberia haber otro tipo "+ ontoBindingItem.class)
                     
                     changeItems[ontoBindingItem] = newOntoBindingItem
                  }
               }
               // Saca el item con codigo viejo y pone el item con codigo sobreescrito
               changeItems.each {viejo, nuevo ->
                  ontoBinding.bindingList.remove(viejo)
                  ontoBinding.bindingList.add(nuevo)
               }
               
               walkt.root.ontology."$attr" << ontoBinding
            }
         }
      }
      
      
      // CCCPrimitiveObject
      walk.observeCPO { node, parchetype, walkt, parent ->
         
         println "PrimitiveObject: "+ node.class +" "+ parent
      }
      walk.observeCPrimitive { node, parchetype, walkt, parent ->
         
         println "Primitive: "+ node.class +" "+ parent
      }
      
      // Procesa nodos ConstraintRef que tienen codigos acNNNN
      walk.observeCR { node, parchetype, walkt, parent ->
         
         // reference: acNNNN, rmTypeName: CodePhrase, path: /../..
         println "ConstraintRef: "+ node.reference +" "+ node.rmTypeName +" "+ node.path() //  +" "+ parent
         
         def newCode = walkt.codeMapping.transformCode(parchetype.archetypeId.value, node.reference)
         node.reference = newCode
      }
      
      
      walk.init(archetype)
      walk.start()
      
      
      // Escribir ADL
      
      def outputter = new ADLSerializer()
      def out = new StringWriter()
      outputter.output(walk.root, out)
      println out.toString()
      
      
      
      /* TIENE TODOS LOS NODEID sobreescritos!!!
       * definition
          INSTRUCTION[at1234] matches {
              activities cardinality matches {0..*; unordered} matches {
                  ACTIVITY[at1234] occurrences matches {0..1} matches {
                      action_archetype_id matches {/openEHR-EHR-ACTION\.test_ordenes\.v1/}
                      description matches {
                          ITEM_TREE[at1234] matches {
                              items cardinality matches {0..*; unordered} matches {
                                  ELEMENT[at1234] occurrences matches {0..1} matches {
                                      value matches {
                                          DV_TEXT matches {*}
                                      }
                                  }
                                  ELEMENT[at1234] occurrences matches {0..1} matches {
                                      value matches {
                                          DV_DATE_TIME matches {*}
                                      }
                                  }
                                  ELEMENT[at1234] occurrences matches {0..1} matches {
                                      value matches {
                                          DV_COUNT matches {*}
                                      }
                                  }
                                  ELEMENT[at1234] occurrences matches {0..1} matches {
                                      value matches {
                                          DV_BOOLEAN matches {
                                              value matches {true, false}
                                          }
                                      }
                                  }

       */
   }
}