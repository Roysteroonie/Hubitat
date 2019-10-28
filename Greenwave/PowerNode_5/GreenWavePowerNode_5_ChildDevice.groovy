/*****************************************************************************************************************
 *  Copyright: Nick Veenstra
 *
 *  Name: GreenWave PowerNode 6 Child Device
 *
 *  Date: 2018-01-04
 *
 *  Version: 1.00
 *
 *  Source and info: https://github.com/CopyCat73/SmartThings/tree/master/devicetypes/copycat73/greenwave-powernode-6-child-device.src
 *
 *  Author: Nick Veenstra
 *  Thanks to Eric Maycock for code inspiration 
 *
 *  Description: Device handler for the GreenWave PowerNode (multi socket) Z-Wave power outlet child nodes
 *
 *  License:
 *   Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *   for the specific language governing permissions and limitations under the License.
 *
 *   30/08/2019 Updated by @Royski to work with Hubitat, with help from @chuck.schwer and @stephack 
 *	 Solution here https://community.hubitat.com/t/parent-child-device-not-actioning-on-off/21694/30?u=royski
 *
 *****************************************************************************************************************/
metadata {
	definition (name: "GreenWave PowerNode 5 Child Device", namespace: "copycat73", author: "Nick Veenstra") {
		capability "Switch"
		capability "Energy Meter"
		capability "Power Meter"
		capability "Refresh"
		
		attribute "lastupdate", "string"
		command "reset"
	}
	
	preferences {
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
	}
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def installed() {
	log.debug "Greenwave child installed"
}

def updated(){
log.info "Greenwave child updated"
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)
	parent.updateChildLabel(splitChannel(device.deviceNetworkId))
}

def on() {
	if (logEnable) log.debug "$device.label - On"
	parent.switchOn(splitChannel(device.deviceNetworkId))
}

def off() {
	if (logEnable) log.debug "$device.label - Off"
	parent.switchOff(splitChannel(device.deviceNetworkId))
}

def refresh() {
	if (logEnable) log.debug "$device.label - Refresh"
	parent.pollNode(splitChannel(device.deviceNetworkId))
}

def reset() {
	if (logEnable) log.debug "$device.label - Reset"
	parent.resetNode(splitChannel(device.deviceNetworkId))
}

private splitChannel(String channel) {
    channel.split("-ep") [-1] as Integer
}
