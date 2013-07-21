package test

import groovy.util.GroovyTestCase
import org.openehr.am.serialize.ADLSerializer

import com.cabolabs.openehr.tools.flattener.ArchetypeManager;
import com.cabolabs.openehr.tools.flattener.ArchetypeWalkthrough

class WalkthroughTest extends GroovyTestCase {

   /*
    * Carga arquetipo sin slots, lo recorre, y deberia dejar el mismo arquetipo con los mismos codigos porque no tiene slots.
    */
   void testWalkWithClosure()
   {
      def f = new File(".")
      println "Current folder: "+ f.getCanonicalPath()
      
      
      
      
      // Load archetype
      def loader = ArchetypeManager.getInstance("./src/test/archetypes")
      def archetype = loader.getArchetype("openEHR-EHR-INSTRUCTION.test_ordenes.v1")
      
      
      
      def walk = new ArchetypeWalkthrough()
      
      
      
      
      // Observer CComplexObject
      // parchetype es el arquetipo padre del node
      // 
      walk.observeCCO { node, parchetype, walkt ->

         println "CCO: "+ parchetype.archetypeId.value + node.path() +" "+ 
                          node.rmTypeName +" "+ node.nodeId
         
         // sobreescribe nodeID
         if (node.nodeID)
         {
            def newCode = walkt.codeMapping.transformCode(parchetype.archetypeId.value, node.nodeID)
            node.setNodeId( newCode )
         }
      }
      
      // Slot
      walk.observeAS { node, parchetype, walkt ->
      
         println "Slot: "+ parchetype.archetypeId.value + node.path() +" "
      }
      
      walk.observeOAT { node, parchetype, walkt ->
      
         println "Ontology Archetype Term: "+ node.code +" "+ node.text +" "+ node.description
         
         // Codigo seteado en el mapeo del CComplexObject
         def mappedCode = walkt.codeMapping.getMappedCode(parchetype.archetypeId.value, node.code)
         node.code = mappedCode
      }
      walk.observeOBI { node, parchetype, walkt ->
      
         println "Ontology Binding Item: "+ node.class +" "+ node.code
         
         // Codigo seteado en el mapeo del CComplexObject
         def mappedCode = walkt.codeMapping.getMappedCode(parchetype.archetypeId.value, node.code)
         node.code = mappedCode
      }
      walk.observeOTBI { node, parchetype, walkt ->
      
         println "Ontology Term Binding Item: "+ node.class +" "+ node.code +" "+ node.text +" "+ node.description
         
         // Codigo seteado en el mapeo del CComplexObject
         def mappedCode = walkt.codeMapping.getMappedCode(parchetype.archetypeId.value, node.code)
         node.code = mappedCode
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