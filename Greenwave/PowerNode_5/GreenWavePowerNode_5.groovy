/*****************************************************************************************************************
 *  Copyright: Nick Veenstra
 *
 *  Name: GreenWave PowerNode 6
 *
 *  Date: 2018-01-04
 *
 *  Version: 1.00
 *
 *  Source and info: https://github.com/CopyCat73/SmartThings/tree/master/devicetypes/copycat73/greenwave-powernode-6.src
 *
 *  Author: Nick Veenstra
 *  Thanks to David Lomas, Cooper Lee and Eric Maycock for code inspiration 
 *
 *  Description: Device handler for the GreenWave PowerNode (multi socket) Z-Wave power outlet
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
	definition (name: "GreenWave PowerNode 5", namespace: "copycat73", author: "Nick Veenstra", ocfDeviceType: "oic.d.switch") {
		capability "Energy Meter"
		capability "Switch"
		capability "Power Meter"
		capability "Polling"
		capability "Refresh"
		capability "Configuration"
	
		attribute "switch", "string"
		attribute "switch1", "string"
		attribute "switch2", "string"
		attribute "switch3", "string"
		attribute "switch4", "string"
		attribute "switch5", "string"
		//attribute "switch6", "string"
		attribute "power", "string"
		attribute "power1", "string"
		attribute "power2", "string"
		attribute "power3", "string"
		attribute "power4", "string"
		attribute "power5", "string"
		//attribute "power6", "string"
		attribute "energy", "string"
		attribute "energy1", "string"
		attribute "energy2", "string"
		attribute "energy3", "string"
		attribute "energy4", "string"
		attribute "energy5", "string"
		//attribute "energy6", "string"
		attribute "lastupdate", "string"

		command "on"
		command "off"
		command "on1"
		command "off1"
		command "on2"
		command "off2"
		command "on3"
		command "off3"
		command "on4"
		command "off4"
		command "on5"
		command "off5"
		command "reset"

		fingerprint inClusters : "0x25,0x32,0x60,0x72,0x86,0x20,0x71,0x70,0x27,0x85,0x87,0x75,0x56"
		fingerprint mfr:"0099", prod:"0003", model:"0004", deviceJoinName: "GreenWave PowerNode 5"
		
	}
	
	preferences {
		// input name:"updateLight", type:"number", title:"After how many minutes the GreenWave device should start flashing if the controller didn't communicate with this device", defaultValue:255

		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
	}
	
}
/*****************************************************************************************************************
 *  SmartThings System Commands:
 *****************************************************************************************************************/

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def parse(String description) {
	def result = null
	def cmd = zwave.parse(description, [0x60:3])
	if (cmd) {
		result = zwaveEvent(cmd)
		//log.debug "Parsed ${cmd} to ${result.inspect()}"
	} else {
		//log.debug "Non-parsed event: ${description}"
	}
	return result
}

def installed() {
	log.debug "installed"

	createChildDevices()

	//command(zwave.manufacturerSpecificV1.manufacturerSpecificGet())
	//command(zwave.configurationV1.configurationSet(configurationValue: [255], parameterNumber: 1, size: 1))

}

def uninstalled() {
	log.debug "uninstalled()"
	if (childDevices) {
		log.debug "removing child devices"
		removeChildDevices(getChildDevices())
	}
}

private removeChildDevices(delete) {
	delete.each {
		deleteChildDevice(it.deviceNetworkId)
	}
}

def refresh() {
	log.info "Refresh"
	pollNodes()
}

def updated(){
log.info "Greenwave Parent updated"
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)
}


def initialize() {
	unschedule()
	runEvery5Minutes(pollNodes)
}

def createChildDevices() {
	log.debug "creating child devices"
		
	try {
		for (i in 1..5) {
		def node = i as String
		def devLabel = "Greenwave switch "+node
		addChildDevice("copycat73", "GreenWave PowerNode 5 Child Device", "${device.deviceNetworkId}-ep${i}", [completedSetup: true, label: devLabel,
			isComponent: false, componentName: "switch$i", componentLabel: devLabel])    
		}
	} catch (e) {
		log.debug "${e}"
		showAlert("Child device creation failed. Please make sure that the \"GreenWave PowerNode 6 Child Device\" is installed and published.","childDeviceCreation","failed")
	}
}

private showAlert(text,name,value) {
	sendEvent(
		descriptionText: text,
		eventType: "ALERT",
		name: name,
		value: value,
		displayed: true,
	)
}

def configure() {
	if (txtEnable) log.info "configure()"
	def cmds = []
	cmds << zwave.configurationV1.configurationSet(configurationValue: [255], parameterNumber: 1, size: 1).format()
	delayBetween(cmds)
}


def on() {
	log.info "on"
	[    	
		zwave.basicV1.basicSet(value: 0xFF).format(),
		zwave.switchBinaryV1.switchBinaryGet().format(),
		"delay 3000",
		zwave.meterV2.meterGet(scale: 2).format()
	]
}
def off() {
	log.info "off"
	[
		zwave.basicV1.basicSet(value: 0x00).format(),
		zwave.switchBinaryV1.switchBinaryGet().format(),
		"delay 3000",
		zwave.meterV2.meterGet(scale: 2).format()
	]
}
def on1() {
	switchOn(1)
}
def off1() {
	switchOff(1)
}
def on2() {
	switchOn(2)
}
def off2() {
	switchOff(2)
}
def on3() {
	switchOn(3)
}
def off3() {
	switchOff(3)
}
def on4() {
	switchOn(4)
}
def off4() {
	switchOff(4)
}
def on5() {
	switchOn(5)
}
def off5() {
	switchOff(5)
}

void switchOn(Integer node) {
	log.info "${device.label} node ${node} On"
	def cmds = []
	cmds << command(encap(zwave.basicV1.basicSet(value: 0xFF), node))
	cmds << command(encap(zwave.switchBinaryV1.switchBinaryGet(), node))
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZWAVE))
}

void switchOff(node) {
	log.info "${device.label} node ${node} Off"
	def cmds = []
	cmds << command(encap(zwave.basicV1.basicSet(value: 0x00), node))
	cmds << command(encap(zwave.switchBinaryV1.switchBinaryGet(), node))
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZWAVE))
}


def poll() {
	pollNodes()
}

def pollNodes() {
	if (logEnable) log.debug "Polling Powerstrip - ${device.label} node ${node}"
	if (txtEnable) log.info "Polling Powerstrip Nodes"
	def cmds = []
	for ( i in 1..5 ) { 
		cmds << command(encap(zwave.switchBinaryV1.switchBinaryGet(), i))
		cmds << command(encap(zwave.meterV2.meterGet(scale:0),i))
		cmds << command(encap(zwave.meterV2.meterGet(scale:2),i))
	}
	cmds << zwave.switchBinaryV1.switchBinaryGet().format() 
	cmds << zwave.meterV2.meterGet(scale:0).format()
	cmds << zwave.meterV2.meterGet(scale:2).format()
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZWAVE))
}

def pollNode(endpoint)  {
	if (logEnable) log.debug "Polling Powerstrip node ${endpoint}"
	def cmds = []
	cmds << command(encap(zwave.switchBinaryV1.switchBinaryGet(),endpoint))
	cmds << command(encap(zwave.meterV2.meterGet(scale:0),endpoint))
	cmds << command(encap(zwave.meterV2.meterGet(scale:2),endpoint))
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZWAVE))
}

def updateChildLabel(endpoint) {
	log.debug "update tile label for endpoint $endpoint"
	// tbd
}

def lastUpdated(time) {
	def timeNow = now()
	def lastUpdate = ""
	if(location.timeZone == null) {
		if (logEnable) log.debug "Cannot set update time : location not defined in app"
	}
	else {
		lastUpdate = new Date(timeNow).format("MMM dd yyyy HH:mm", location.timeZone)
	}
	return lastUpdate
}

def ping() {
	if (logEnable) log.debug "ping() called"
	refresh()
}

def reset() {
	log.info "Resetting kWh for all endpoints"
	def cmds = []
	for ( i in 1..5 ) { 
		cmds << command(encap(zwave.meterV2.meterReset(), i))
		cmds << command(encap(zwave.meterV2.meterGet(scale:0),i))
	}
	cmds << command(zwave.meterV2.meterReset())
	cmds << command(zwave.meterV2.meterGet(scale:0))
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZWAVE))
}

def resetNode(endpoint) {
	log.info "Resetting kWh for endpoint $endpoint"
	def cmds = []
	cmds << command(encap(zwave.meterV2.meterReset(),endpoint))
	cmds << command(encap(zwave.meterV2.meterGet(scale:0),endpoint))
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZWAVE))
}


private encap(cmd, endpoint) {
	if (endpoint) {
		zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint:endpoint).encapsulate(cmd)
	} else {
		cmd
	}
}

private command(hubitat.zwave.Command cmd) {
	if (state.sec) {
		zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	} else {
		cmd.format()
	}
}

private commands(commands, delay=1000) {
	delayBetween(commands.collect{ command(it) }, delay)
}

/*****************************************************************************************************************
 *  Z-wave Event Handlers.
 *****************************************************************************************************************/


def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd, ep=null)
{
	if (logEnable) log.debug "Greenwave v1 basic report received"
	if (ep) {
		def childDevice = childDevices.find{it.deviceNetworkId == "$device.deviceNetworkId-ep$ep"}
		if (childDevice)
		childDevice.sendEvent(name: "switch", value: cmd.value ? "on" : "off")
	} else {
		def result = createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "digital")
		def cmds = []
		cmds << encap(zwave.switchBinaryV1.switchBinaryGet(), 1)
		cmds << encap(zwave.switchBinaryV1.switchBinaryGet(), 2)
		
		return result
	}
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd, ep=null) {
   
   if (logEnable) log.debug "Greenwave v1 switchbinary report received for endpoint $ep value $cmd.value"
   if (ep) {
		def childDevice = childDevices.find{it.deviceNetworkId == "$device.deviceNetworkId-ep$ep"}
		if (childDevice)
			childDevice.sendEvent(name: "switch", value: cmd.value ? "on" : "off")
			childDevice.sendEvent(name: 'lastupdate', value: lastUpdated(now()), unit: "")
	} else {
		def result = createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "digital")
		sendEvent(name: 'lastupdate', value: lastUpdated(now()), unit: "")
		def cmds = []
		cmds << encap(zwave.switchBinaryV1.switchBinaryGet(), 1)
		cmds << encap(zwave.switchBinaryV1.switchBinaryGet(), 2)
		
		return result
	}
}

def zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd, ep=null)
{
	if (logEnable) log.debug "Greenwave v3 meter report received for endpoint $ep scale $cmd.scale value $cmd.scaledMeterValue"
	def result
	def cmds = []
	if (cmd.scale == 0) {
		result = [name: "energy", value: cmd.scaledMeterValue, unit: "kWh"]
	} else if (cmd.scale == 1) {
		result = [name: "energy", value: cmd.scaledMeterValue, unit: "kVAh"]
	} else {
		result = [name: "power", value: cmd.scaledMeterValue, unit: "W"]
	}
	if (ep) {//
		def childDevice = childDevices.find{it.deviceNetworkId == "$device.deviceNetworkId-ep$ep"}
		if (childDevice)
			childDevice.sendEvent(result)
	} else {
	   sendEvent(name: 'lastupdate', value: lastUpdated(now()), unit: "")
	   sendEvent(result)
	   (1..5).each { endpoint ->
			cmds << encap(zwave.meterV2.meterGet(scale: 0), endpoint)
			cmds << encap(zwave.meterV2.meterGet(scale: 2), endpoint)
	   }
	   
	   return result
	}
}



def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
	if (logEnable) log.debug "Greenwave v3 cMultiChannelCmdEncap command received"
	def encapsulatedCommand = cmd.encapsulatedCommand([0x30: 1, 0x31: 1]) 
	if (encapsulatedCommand) {
		return zwaveEvent(encapsulatedCommand,cmd.sourceEndPoint)
	}   
}


def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
	if (logEnable) log.debug "Greenwave v1 configuration report received"
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
	if (logEnable) log.debug "Greenwave v2 configuration report received"
}


def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCapabilityReport cmd) {
	if (logEnable) log.debug "Greenwave v3 multi channel capability report received"
}
