/**
 *  IKEA Tr&aring;dfri Dimmer
 *
 *  Copyright 2017 Jonas Laursen
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Updated by Royski to change the clockwise and anti-clockwise on/off
 */
metadata {
	definition (name: "IKEA Trådfri Dimmer as Switch", namespace: "dk.decko", author: "Jonas Laursen") {
        capability "Sensor"
		capability "Configuration"
		capability "Switch"
        capability "Refresh"
		
	fingerprint endpointId: "01", profileId: "0104", deviceId: "0810", deviceVersion: "02", inClusters: "0000, 0001, 0003, 0009, 0B05, 1000", outClusters: "0003, 0004, 0006, 0008, 0019, 1000"
	//fingerprint endpointId: "01", profileId: "C05E", deviceId: "0810", deviceVersion: "02", inClusters: "0000, 0001, 0003, 0009, 0B05, 1000", outClusters: "0003, 0004, 0006, 0008, 0019, 1000"
	}
	
	main("switch")
}

preferences {
	
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
	
}

def logsOff(){
	log.warn "debug logging disabled..."
	device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def updated(){
log.info "updated..."
	log.warn "debug logging is: ${logEnable == true}"
	log.warn "description logging is: ${txtEnable == true}"
	if (logEnable) runIn(1800,logsOff)
}

// parse events into attributes
def parse(String description) {
	//log.debug "Catch all: $description"
	if (logEnable) log.debug zigbee.parseDescriptionAsMap(description)

	def map = zigbee.parseDescriptionAsMap(description)
		if (description.endsWith("00 0000 05 00 00C3")) {
			// Start Turn clockwise
			if (txtEnable) log.info "Dimmer on"
			sendEvent(name: "switch", value: "on")
			
			// Start Turn clockwise
		} else if (description.endsWith("00 0000 01 00 01C3")) {
			if (txtEnable) log.info "Dimmer Off"
			sendEvent(name: "switch", value: "off")
	}
}
  

def off() {
	sendEvent(name: "switch", value: "off")
}

def on() {
	sendEvent(name: "switch", value: "on")
}


def refresh() {
	log.debug "Dimmer Refresh"
    zigbee.onOffRefresh() + zigbee.onOffConfig()
}

def configure() {
	if (txtEnable) log.debug "Configure called"
	["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 8 {${device.zigbeeId}} {}"]
}

