/**
 *
 *  Raspberry Pi Stats
 *
 *  Copyright Janos Elohazi
 *  Copyright Scott Grayban
 *
 *  Ported to Hubitat by Scott Grayban with help from ogiewon
 *
 *  Monitor your Raspberry Pi using SmartThings and Raspberry Pi Monitor <https://github.com/cl0udninja/raspberrypi.monitor>
 *
 *  *************************************************************************
 *  Licensed under the GNU v3 (https://www.gnu.org/licenses/gpl-3.0.en.html)
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *  *************************************************************************
 *
 *  Changes:
 *   1.0.5 - Merged Royski edits that adds auto refresh for stats update
 *
 *   1.0.4 - Redefined the imprt url (Royski)
 *
 *   1.0.3 - Added auto logging off after 30 minutes (Royski)
 *
 *   1.0.2 - Added debug switch
 *         - Added version update check (Cobra)
 *         - Removed Tiles section HE doesn't use them
 *         - Lots of code cleanup
 *
 *   1.0.1 - Supports boards up to Raspberry Pi 3A+
 *         - Added attribute freeMemoryPercent
 *
 *   1.0.0 - Initial port
 *
*/

import groovy.json.*
	
metadata {
	definition (
		name: "Raspberry Pi Stats",
		namespace: "sgrayban",
		author: "Scott Grayban",
		importUrl: "https://raw.githubusercontent.com/sgrayban/Hubitat-Ports/master/Drivers/rpi/Raspberry-Pi-Stats.groovy"
		)
	{
	capability "Polling"
	capability "Refresh"
	capability "Temperature Measurement"
	capability "Sensor"
        
	attribute "cpuFrequency", "number"       
	attribute "freeMemory", "number"
	attribute "freeMemoryPercent", "number"
	attribute "cpuCoreVoltage", "number"
	attribute "modelName", "string"
	attribute "boardType", "string"
	attribute "javaVersion", "string"
	attribute "hostname", "string"
	attribute "serialNumber", "string"
	attribute "DriverVersion", "string"
	attribute "DriverAuthor", "string"
	attribute "DriverStatus", "string"
	attribute "LastRefresh", "string"
	}

preferences {		
	input("ip", "string", title:"IP Address", description: "cpuTemperature", defaultValue: "" ,required: true, displayDuringSetup: true)
	input("port", "string", title:"Port", description: "8080", defaultValue: "8080" , required: true, displayDuringSetup: true)	
	input "refreshEvery", "enum", title: "Enable auto refresh every XX Minutes", required: false, defaultValue: false, //RK
			options: [5:"5 minutes",10:"10 minutes",15:"15 minutes",30:"30 minutes"] //RK
	input "locale", "enum", title: "Choose refresh date format", required: true, defaultValue: true, //RK
			options: [US:"US MM/DD/YYYY",UK:"UK DD/MM/YYYY"] //RK
	input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
	input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true //RK
	}
}

def installed() {
	log.debug "installed"
	initialize();
}

// App Version   *********************************************************************************
def setVersion(){
    state.Version = "1.0.5"
    state.InternalName = "RaspberryPiStats"

    sendEvent(name: "DriverAuthor", value: "sgrayban")
    sendEvent(name: "DriverVersion", value: state.Version)
    sendEvent(name: "DriverStatus", value: state.Status)
}

def updated() {
	log.info "Preferences updated..."
	log.warn "Debug logging is: ${debugOutput == true}"
	unschedule()
	"runEvery${refreshEvery}Minutes"(autorefresh) //RK
	log.info "Refresh set for every ${refreshEvery} Minutes" //RK
	if (debugOutput) runIn(1800,logsOff)
	state.LastRefesh = new Date().format("YYYY/MM/dd \n HH:mm:ss", location.timeZone) //RK
	version()
	initialize();
}

def ping() {
	logDebug "ping"
	poll()
}

def initialize() {
	log.info "initialize"
	if (txtEnable) log.info "initialize" //RK
	refresh()
}

def logsOff(){
	log.warn "debug logging auto disabled..."
	device.updateSetting("debugOutput",[value:"false",type:"bool"])
}

// parse events into attributes
def parse(description) {
    logDebug "Parsing '${description}'"
	
    def msg = parseLanMessage(description)
    def headerString = msg.header
    def bodyString   = msg.body

    logDebug "received body:\n${bodyString}"
    
    if(bodyString.trim() == "ok")
	return
    
    def json = null;
    try{
	json = new groovy.json.JsonSlurper().parseText(bodyString)
        logDebug "${json}"
        
        if(json == null){
	logDebug "body object not parsed"
            return
        }
    }
    catch(e){
	log.error("Failed to parse json e = ${e}")
        return
    }

    logDebug "JSON '${json}'"
    
    if (json.containsKey("cpuTemperature")) {
	if (getTemperatureScale() == "C") {
		sendEvent(name: "temperature", value: json.cpuTemperature)
	} else {
	def fahrenheit = json.cpuTemperature * 9 / 5 + 32
		sendEvent(name: "temperature", value: fahrenheit)
        }
    }
    if (json.containsKey("freeMemory")) {
		sendEvent(name: "freeMemory", value: (json.freeMemory/1024/1024).toDouble().round(2))
	if (json.containsKey("totalMemory")) {
		sendEvent(name: "freeMemoryPercent", value: (json.freeMemory/json.totalMemory*100).toDouble().round())
	}
    }
    if (json.containsKey("cpuCoreVoltage")) {
	sendEvent(name: "cpuCoreVoltage", value: json.cpuCoreVoltage)
    }
    if (json.containsKey("modelName")) {
	sendEvent(name: "modelName", value: json.modelName)
    }
    if (json.containsKey("boardType")) {
	sendEvent(name: "boardType", value: json.boardType)
    }
    if (json.containsKey("javaVersion")) {
	sendEvent(name: "javaVersion", value: json.javaVersion)
    }
    if (json.containsKey("hostname")) {
	sendEvent(name: "hostname", value: json.hostname)
    }
    if (json.containsKey("serialNumber")) {
	sendEvent(name: "serialNumber", value: json.serialNumber)
    }
}

// handle commands
//RK Updated to include last refreshed
def poll() {
	if (locale == "UK") {
	if (debugOutput) log.info "Get last UK Date DD/MM/YYYY"
    state.LastRefresh = new Date().format("d/MM/YYYY \n HH:mm:ss", location.timeZone)
    sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	} 
	if (locale == "US") {
	if (debugOutput) log.info "Get last US Date MM/DD/YYYY"
    state.LastRefresh = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
    sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	}
	if (txtEnable) log.info "Executing 'poll'" //RK
	getPiInfo()
}

def refresh() {
	if (locale == "UK") {
	if (debugOutput) log.info "Get last UK Date DD/MM/YYYY"
    state.LastRefresh = new Date().format("d/MM/YYYY \n HH:mm:ss", location.timeZone)
    sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	} 
	if (locale == "US") {
	if (debugOutput) log.info "Get last US Date MM/DD/YYYY"
    state.LastRefresh = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
    sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	}
	if (txtEnable) log.info "Executing 'manual refresh'" //RK
	getPiInfo()
}

def autorefresh() {
	if (locale == "UK") {
	if (debugOutput) log.info "Get last UK Date DD/MM/YYYY"
    state.LastRefresh = new Date().format("d/MM/YYYY \n HH:mm:ss", location.timeZone)
    sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	} 
	if (locale == "US") {
	if (debugOutput) log.info "Get last US Date MM/DD/YYYY"
    state.LastRefresh = new Date().format("MM/d/YYYY \n HH:mm:ss", location.timeZone)
    sendEvent(name: "LastRefresh", value: state.LastRefresh, descriptionText: "Last refresh performed")
	}
	if (txtEnable) log.info "Executing 'auto refresh'" //RK
	getPiInfo()

}
//RK Last Refresh End

private getPiInfo() {
	def iphex = convertIPtoHex(ip)
	def porthex = convertPortToHex(port)
    
	def uri = "/api/pi"
	def headers=[:]
	headers.put("HOST", "${ip}:${port}")
	headers.put("Accept", "application/json")

	def hubAction = new hubitat.device.HubAction
	(
		method: "GET",
		path: uri,
		headers: headers,
		"${iphex}:${porthex}",
		[callback: parse]
	)
	logDebug "Getting Pi data ${hubAction}"
	hubAction 
}

private String convertIPtoHex(ipAddress) {
	logDebug "convertIPtoHex ${ipAddress} to hex"
	String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
	return hex
}

private String convertPortToHex(port) {
	logDebug "convertPortToHex ${port} to hex"
	String hexport = port.toString().format( '%04x', port.toInteger() )
	return hexport
}

private Integer convertHexToInt(hex) {
	return Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	return [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}
def sync(ip, port) {
	logDebug "sync ${ip} ${port}"
	def existingIp = getDataValue("ip")
	def existingPort = getDataValue("port")
	if (ip && ip != existingIp) {
		updateDataValue("ip", ip)
	}
	if (port && port != existingPort) {
		updateDataValue("port", port)
	}
	def ipHex = convertIPToHex(ip)
	def portHex = convertPortToHex(port)
	device.deviceNetworkId = "${ipHex}:${portHex}"
}

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) {
	log.debug "$msg"
	}
}

private dbCleanUp() {
	unschedule()
	state.remove("version")
	state.remove("Version")
}

// Check Version   ***** with great thanks and acknowlegment to Cobra (github CobraVmax) for his original code **************
def version(){
	updatecheck()
	schedule("0 0 18 1/1 * ? *", updatecheck) // Cron schedule
//	schedule("0 0/1 * 1/1 * ? *", updatecheck) // Test Cron schedule
}

def updatecheck(){
    setVersion()
     def paramsUD = [uri: "https://sgrayban.github.io/Hubitat-Public/version.json"]
      try {
            httpGet(paramsUD) { respUD ->
                  //  log.warn " Version Checking - Response Data: ${respUD.data}"   // Troubleshooting Debug Code - Uncommenting this line should show the JSON response from your webserver
                  def copyrightRead = (respUD.data.copyright)
                  state.Copyright = copyrightRead
                  def newVerRaw = (respUD.data.versions.Driver.(state.InternalName))
                  def newVer = (respUD.data.versions.Driver.(state.InternalName).replace(".", ""))
                  def currentVer = state.Version.replace(".", "")
                  state.UpdateInfo = (respUD.data.versions.UpdateInfo.Driver.(state.InternalName))
                  state.author = (respUD.data.author)
                  if(newVer == "NLS"){
                       state.Status = "<b>** This driver is no longer supported by $state.author  **</b>"       
                       log.warn "** This driver is no longer supported by $state.author **"      
                  }           
                  else if(currentVer < newVer){
                       state.Status = "<b>New Version Available (Version: $newVerRaw)</b>"
                       log.warn "** There is a newer version of this driver available  (Version: $newVerRaw) **"
                       log.warn "** $state.UpdateInfo **"
                 } 
                 else if(currentVer > newVer){
                       state.Status = "<b>You are using a Test version of this Driver (Version: $newVerRaw)</b>"
                 }
                 else{ 
                     state.Status = "Current"
                     log.info "You are using the current version of this driver"
                 }
            } // httpGet
      } // try

      catch (e) {
           log.error "Something went wrong: CHECK THE JSON FILE AND IT'S URI -  $e"
      }

      if(state.Status == "Current"){
           state.UpdateInfo = "Up to date"
           sendEvent(name: "DriverUpdate", value: state.UpdateInfo)
           sendEvent(name: "DriverStatus", value: state.Status)
      }
      else {
           sendEvent(name: "DriverUpdate", value: state.UpdateInfo)
           sendEvent(name: "DriverStatus", value: state.Status)
      }

      sendEvent(name: "DriverAuthor", value: "sgrayban")
      sendEvent(name: "DriverVersion", value: state.Version)
}
