/**
 *  Based on code originally copyright 2019 SmartThings
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 * NOTES:
 * Modified from ST public DTH to remove other button device code
 * Minor changes to work with Hubitat
 *
 * Version: 0.1 BETA
 *
 */

import com.hubitat.zigbee.DataType

metadata {
	definition (name: "Ikea Tradfri Dimmer (Puck)", namespace: "RMoRobert", author: "SmartThings", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/IkeaTradfriPuck/IkeaTradfriPuck.groovy") {
		capability "Actuator"
		capability "Battery"
		capability "Configuration"
		//capability "Health Check"
		capability "Switch"
		capability "Switch Level"
   		command "ClearStates"				// Clear all device states 

        //fingerprint profileId: "0104", inClusters: "0000,0001,0003,0020,0B05", outClusters: "0003,0006,0008,0019", manufacturer: "Centralite Systems", model: "3131-G", deviceJoinName: "Centralite Smart Switch"
		//fingerprint profileId: "0104", inClusters: "0000,0001,0003,0020,FC11", outClusters: "0003,0004,0006,0008,FC10", manufacturer: "sengled", model: "E1E-G7F", deviceJoinName: "Sengled Smart Switch"
		fingerprint manufacturer: "IKEA of Sweden", model: "TRADFRI wireless dimmer", deviceJoinName: "IKEA TRÅDFRI Wireless dimmer" // 01 [0104 or C05E] 0810 02 06 0000 0001 0003 0009 0B05 1000 06 0003 0004 0006 0008 0019 1000
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

def updated(){
log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)
	response(configure())
}

def getDOUBLE_STEP() { 10 }
def getSTEP() { 5 }

def getONOFF_ON_COMMAND() { 0x0001 }
def getONOFF_OFF_COMMAND() { 0x0000 }
def getLEVEL_MOVE_LEVEL_COMMAND() { 0x0000 }
def getLEVEL_MOVE_COMMAND() { 0x0001 }
def getLEVEL_STEP_COMMAND() { 0x0002 }
def getLEVEL_STOP_COMMAND() { 0x0003 }
def getLEVEL_MOVE_LEVEL_ONOFF_COMMAND() { 0x0004 }
def getLEVEL_MOVE_ONOFF_COMMAND() { 0x0005 }
def getLEVEL_STEP_ONOFF_COMMAND() { 0x0006 }
def getLEVEL_STOP_ONOFF_COMMAND() { 0x0007 }
def getLEVEL_DIRECTION_UP() { "00" }
def getLEVEL_DIRECTION_DOWN() { "01" }

def getBATTERY_VOLTAGE_ATTR() { 0x0020 }
def getBATTERY_PERCENT_ATTR() { 0x0021 }

def getMFR_SPECIFIC_CLUSTER() { 0xFC10 }

def getUINT8_STR() { "20" }

def parse(String description) {
	if (logEnable) log.debug "description is $description"
	def results = []
	def event = zigbee.getEvent(description)
	if (event) {
	//Events
        if (txtEnable) log.warn "EVENT ${event}"
		results << createEvent(event)
	} else {
		def descMap = zigbee.parseDescriptionAsMap(description)
		if (descMap.clusterInt == 0x0001) { // power configuration
			results = handleBatteryEvents(descMap)
		} else if (descMap.clusterInt == 0x0000) { // on/off
			results = handleSwitchEvent(descMap)
		} else if (descMap.clusterInt == 0x0008) { //level control
			results = handleIkeaDimmerLevelEvent(descMap)
		} else {
			if (txtEnable) log.warn "DID NOT PARSE MESSAGE for description : $description"
			if (logEnable) log.debug "${descMap}"
		}
	}

	if (logEnable) log.debug "parse returned $results"
	return results
}

def handleIkeaDimmerLevelEvent(descMap) {
	def results = []
    int cmd = Integer.parseInt(descMap.command)
	if (cmd == LEVEL_STEP_COMMAND) {
        if (logEnable) log.debug "level step command"
		results = handleStepEvent(descMap.data[0], descMap)
	} else if (cmd == LEVEL_MOVE_COMMAND || cmd == LEVEL_MOVE_ONOFF_COMMAND) {
        if (logEnable) log.debug "level move"
		// Treat Level Move and Level Move with On/Off as Level Step
		results = handleStepEvent(descMap.data[0], descMap)
	} else if (cmd == LEVEL_STOP_COMMAND || cmd == LEVEL_STOP_ONOFF_COMMAND) {
		// We are not going to handle this event because we are not implementing this the way that the Zigbee spec indicates
		if (logEnable) log.debug "Received stop move - not handling"
	} else if (cmd == LEVEL_MOVE_LEVEL_ONOFF_COMMAND) {
        if (logEnable) log.debug "level move level onoff"
		// The spec defines this as "Move to level with on/off". The IKEA Dimmer sends us 0x00 or 0xFF only, so we will treat this more as a
		// on/off command for the dimmer. Otherwise, we will treat this as off or on and setLevel.
		if (descMap.data[0] == "00") {
			results << createEvent(name: "switch", value: "off", isStateChange: true)
			if (txtEnable) log.info "Switch Off"
			// uncomment if you want to reset the level to Zero when Off
			//results << createEvent(name: "level", value: "0", isStateChange: true)
			//if (logEnable) log.debug "Set level to Zero"
		} else if (descMap.data[0] == "FF") {
			// The IKEA Dimmer sends 0xFF -- this is technically not to spec, but we will treat this as an "on"
			if (device.currentValue("level") == 0) {
			if (logEnable) log.debug "Level change ${level}"
				results << createEvent(name: "level", value: DOUBLE_STEP)
			}
            if (txtEnable) log.info "Switch On"
			results << createEvent(name: "switch", value: "on", isStateChange: true)
		} else {
            if (txtEnable) log.info "Switch On *"
			results << createEvent(name: "switch", value: "on", isStateChange: true)
			// Handle the Zigbee level the same way as we would normally with the same code path -- command(Int?) doesn't matter right now
			// The first byte is the level, the second two bytes are the rate -- we only care about the level right now.
			results << createEvent(zigbee.getEventFromAttrData(descMap.clusterInt, descMap.command, UINT8_STR, descMap.data[0]))
            if (logEnable) log.debug "DATA = ${descMap.data[0]}"
		}
	}

	return results
}

def handleSwitchEvent(descMap) {
	def results = []
    int cmd = Integer.parseInt(descMap.command)
	if (cmd == ONOFF_ON_COMMAND) {
		if (device.currentValue("level") == 0) {
			results << createEvent(name: "level", value: DOUBLE_STEP)
		}
		results << createEvent(name: "switch", value: "on")
	} else if (cmd == ONOFF_OFF_COMMAND) {
		results << createEvent(name: "switch", value: "off")
	}

	return results
}

def handleStepEvent(direction, descMap) {
	def results = []
	def currentLevel = device.currentValue("level") as Integer ?: 0
	def value = null

	if (direction == LEVEL_DIRECTION_UP) {
	//if (txtEnable) log.info "level Up ${currentLevel.value}"
		value = Math.min(currentLevel + DOUBLE_STEP, 100)
	} else if (direction == LEVEL_DIRECTION_DOWN) {
	//if (txtEnable) log.info "level Down ${currentLevel.value}"
		value = Math.max(currentLevel - DOUBLE_STEP, 0)
	}

	if (value != null) {
		if (txtEnable) log.info "Step ${direction == LEVEL_DIRECTION_UP ? "up" : "down"} by $DOUBLE_STEP to $value"

		// don't change level if switch will be turning off
		if (value == 0) {
			results << createEvent(name: "switch", value: "off")
		} else {
			results << createEvent(name: "switch", value: "on")
			results << createEvent(name: "level", value: value)
		}
	} else {
		if (logEnable) log.debug "Received invalid direction ${direction} - descMap.data = ${descMap.data}"
	}

	return results
}

def handleBatteryEvents(descMap) {
	def results = []

	if (descMap.value) {
		def rawValue = zigbee.convertHexToInt(descMap.value)
        if (logEnable) log.debug "rawValue = ${rawValue}"
		def batteryValue = null

		if (rawValue == 0xFF) {
			// Log invalid readings to info for analytics and skip sending an event.
			// This would be a good thing to watch for and form some sort of device health alert if too many come in.
			log.error "Invalid battery reading returned"
		} else if (descMap.attrInt == BATTERY_PERCENT_ATTR) {
			batteryValue = Math.round(rawValue / 2)
		}

		if (batteryValue != null) {
			batteryValue = Math.min(100, Math.max(0, batteryValue))

			results << createEvent(name: "battery", value: batteryValue, unit: "%", descriptionText: "{{ device.displayName }} battery was {{ value }}%", translatable: true)
		}
	}

	return results
}

def off() {
	sendEvent(name: "switch", value: "off", isStateChange: true)
}

def on() {
	sendEvent(name: "switch", value: "on", isStateChange: true)
}

def setLevel(value, rate = null) {
	if (value == 0) {
		sendEvent(name: "switch", value: "off")
	} else {
		sendEvent(name: "switch", value: "on")
		sendEvent(name: "level", value: value)
	}
}

def installed() {
	sendEvent(name: "switch", value: "on")
	sendEvent(name: "level", value: 100)
}

def configure() {
	if (txtEnable) log.debug "Configure"
	//sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 10 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
	zigbee.readAttribute(0x0001, BATTERY_PERCENT_ATTR) +
	zigbee.configureReporting(0x0001 , BATTERY_PERCENT_ATTR, DataType.UINT8, 30, 10 * 60, null)
}

/**
 * Clear States
 *
 * Clears all device states
 *
**/
def ClearStates() {
	log.warn ("ClearStates(): Clearing device states")
	state.clear()
}
