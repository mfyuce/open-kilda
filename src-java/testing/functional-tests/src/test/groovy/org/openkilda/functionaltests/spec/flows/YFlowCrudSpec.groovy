package org.openkilda.functionaltests.spec.flows


import static org.junit.jupiter.api.Assumptions.assumeTrue

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.YFlowHelper
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.testing.service.traffexam.TraffExamService

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Shared

import javax.inject.Provider

@Slf4j
@Narrative("Verify CRUD operations on y-flows.")
class YFlowCrudSpec extends HealthCheckSpecification {
    @Autowired @Shared
    YFlowHelper yFlowHelper
    @Autowired @Shared
    Provider<TraffExamService> traffExamProvider

    @Ignore
    def "Valid y-flow can be created#trafficDisclaimer, covered cases: #coveredCases"() {
        assumeTrue(swT != null, "Some cases can not be covered on given topology: $coveredCases")
        when: "Create a y-flow of certain configuration"
        then: "Y-flow is created and has UP status"
        and: "2 sub-flows are created, visible via regular 'dump flows' API"
        and: "History has relevant entries about y-flow creation"
        and: "User is able to view y-flow paths"
        and: "Y-flow passes flow validation"
        and: "Both sub-flows pass flow validation"
        and: "All involved switches pass switch validation"
        when: "Traffic starts to flow on both sub-flows with maximum bandwidth (if applicable)"
        then: "Traffic flows on both sub-flows, but does not exceed the y-flow bandwidth restriction (~halves for each sub-flow)"
        when: "Delete the y-flow"
        then: "The y-flow is no longer visible via 'get' APIs"
        and: "Related sub-flows are removed"
        and: "History has relevant entries about y-flow deletion"
        and: "All involved switches pass switch validation"
        cleanup: ""
        //remove y-flow

        where:
        //Not all cases may be covered. Uncovered cases will be shown as a 'skipped' test
        data << getSwTripletsTestData()
        swT = data.key as SwitchTriplet
        coveredCases = data.value as List<String>
        trafficDisclaimer = isTrafficApplicable(swT) ? " and pass traffic" : "[!NO TRAFFIC CHECK!]"
    }

    //single-table forbidden?

    @Ignore
    def "Y-flow endpoints cannot conflict with each other"() {
        when: "Try creating a y-flow with one endpoint being in conflict with the other one"
        then: "Error is received, describing the problem"
        and: "'Get' y-flows returns no flows"
        and: "'Get' flows returns no flows"
        where: "Use different types of conflict on endpoints. Conflicts between endpoints and shared endpoint"
        //port-vlan-inner_vlan conflicts between endpoints
        //port-vlan-inner_vlan conflicts between endpoints and shared endpoint. ports and vlan are different cases
        data << []
    }

    def getSwTripletsTestData() {
        def requiredCases = [
                //se = shared endpoint, ep = endpoint, yp = y-point
                [name     : "only se on wb",
                 condition: { SwitchTriplet swT -> swT.shared.wb5164 && swT.ep1 != swT.shared && swT.ep2 != swT.shared && pathsHaveIntersections(swT) }],
                [name     : "only se on non-wb",
                 condition: { SwitchTriplet swT -> !swT.shared.wb5164 && swT.ep1 != swT.shared && swT.ep2 != swT.shared && pathsHaveIntersections(swT) }],
                [name     : "ep on wb, different eps", //ep1 is not the same sw as ep2
                 condition: { SwitchTriplet swT -> ((swT.ep1.wb5164 && swT.ep1 != swT.shared) ||
                         (swT.ep2.wb5164 && swT.ep2 != swT.shared)) && swT.ep1 != swT.ep2 }],
                [name     : "ep on non-wb, different eps", //ep1 is not the same sw as ep2
                 condition: { SwitchTriplet swT -> ((!swT.ep1.wb5164 && swT.ep1 != swT.shared) ||
                         (!swT.ep2.wb5164 && swT.ep2 != swT.shared)) && swT.ep1 != swT.ep2 }],
                [name     : "ep+se on wb",
                 condition: { SwitchTriplet swT -> swT.shared.wb5164 && (swT.ep1 == swT.shared || swT.ep2 == swT.shared) && swT.ep1 != swT.ep2 }],
                [name     : "ep+se on non-wb",
                 condition: { SwitchTriplet swT -> !swT.shared.wb5164 && (swT.ep1 == swT.shared || swT.ep2 == swT.shared) && swT.ep1 != swT.ep2 }],
                [name     : "se+yp on wb",
                 condition: { SwitchTriplet swT -> swT.shared.wb5164 && !pathsHaveIntersections(swT) && (swT.shared != swT.ep1 || swT.shared != swT.ep2) }],
                [name     : "se+yp on non-wb",
                 condition: { SwitchTriplet swT -> !swT.shared.wb5164 && !pathsHaveIntersections(swT) && (swT.shared != swT.ep1 || swT.shared != swT.ep2) }],
                [name     : "ep+yp on wb, not the same as se",
                 condition: { SwitchTriplet swT ->
                     def path1HasEp2 = swT.pathsEp1.find { pathHelper.getInvolvedSwitches(it).find { it == swT.ep2 } }
                     def path2HasEp1 = swT.pathsEp2.find { pathHelper.getInvolvedSwitches(it).find { it == swT.ep1 } }
                     return (swT.ep1.wb5164 && path2HasEp1 && swT.ep1 != swT.shared) ||
                             (swT.ep2.wb5164 && path1HasEp2 && swT.ep2 != swT.shared)
                 }],
                [name     : "ep+yp on non-wb, not the same as se",
                 condition: { SwitchTriplet swT ->
                     def path1HasEp2 = swT.pathsEp1.find { pathHelper.getInvolvedSwitches(it).find { it == swT.ep2 } }
                     def path2HasEp1 = swT.pathsEp2.find { pathHelper.getInvolvedSwitches(it).find { it == swT.ep1 } }
                     return (!swT.ep1.wb5164 && path2HasEp1 && swT.ep1 != swT.shared) ||
                             (!swT.ep2.wb5164 && path1HasEp2 && swT.ep2 != swT.shared)
                 }
                ],
                [name     : "single-sw y-flow on non-wb",
                 condition: { SwitchTriplet swT -> !swT.shared.wb5164 && swT.shared == swT.ep1 && swT.shared == swT.ep2 }
                ],
                [name     : "single-sw y-flow on wb",
                 condition: { SwitchTriplet swT -> swT.shared.wb5164 && swT.shared == swT.ep1 && swT.shared == swT.ep2 }
                ]]
        requiredCases.each { it.picked = false }
        //match all triplets to the list of requirements that it satisfies
        Map<SwitchTriplet, List<String>> weightedTriplets =  topologyHelper.switchTriplets.collectEntries { triplet ->
            [(triplet): requiredCases.findAll { it.condition(triplet) }*.name ]
        }
        //sort, so that most valuable triplet is first
        weightedTriplets = weightedTriplets.sort { - it.value.size() }
        def result = []
        //greedy alg. Pick most valuable triplet. Re-weigh remaining triplets considering what is no longer required and repeat
        while(requiredCases.find { !it.picked } && weightedTriplets.entrySet()[0].value.size() > 0) {
            def pick = weightedTriplets.entrySet()[0]
            weightedTriplets.remove(pick.key)
            pick.value.each {satisfiedCase ->
                requiredCases.find{ it.name == satisfiedCase}.picked = true
            }
            weightedTriplets.entrySet().each { it.value.removeAll(pick.value) }
            weightedTriplets = weightedTriplets.sort { - it.value.size() }
            result << pick
        }
        def notPicked = requiredCases.findAll { !it.picked }
        if (notPicked) {
            //special entry, passing cases that are not covered for later processing
            result << [(null): notPicked*.name]
        }
        return result
    }

    static boolean pathsHaveIntersections(SwitchTriplet swT) {
        swT.pathsEp1.find { path1 ->
            swT.pathsEp2.find { path2 ->
                path1.intersect(path2) != []
            }
        }
    }

    static boolean isTrafficApplicable(SwitchTriplet swT) {
        [swT.shared, swT.ep1, swT.ep2].every { it.traffGens }
    }
}
