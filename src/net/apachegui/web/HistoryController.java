package net.apachegui.web;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;

import net.apachegui.db.LogDataDao;
import net.apachegui.virtualhosts.NetworkInfo;
import net.apachegui.virtualhosts.VirtualHost;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/web/History")
public class HistoryController {
    private static Logger log = Logger.getLogger(HistoryController.class);

    @RequestMapping(value = "/Current", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public String getHistory() throws IOException, InterruptedException {
        int numberEntries = LogDataDao.getInstance().getNumberOfEntries();
        Timestamp newestTimeStamp = LogDataDao.getInstance().getNewestTime();
        String newestTime = (newestTimeStamp == null) ? "" : newestTimeStamp.toString();
        Timestamp oldestTimeStamp = LogDataDao.getInstance().getOldestTime();
        String oldestTime = (oldestTimeStamp == null) ? "" : oldestTimeStamp.toString();

        JSONObject result = new JSONObject();
        result.put("identifier", "id");
        result.put("label", "name");

        JSONArray items = new JSONArray();

        JSONObject entries = new JSONObject();
        entries.put("id", Integer.toString(numberEntries));
        entries.put("numHistory", Integer.toString(numberEntries));
        entries.put("newHistory", newestTime);
        entries.put("oldHistory", oldestTime);

        items.put(entries);
        result.put("items", items);

        return result.toString();
    }

    @RequestMapping(method = RequestMethod.GET, params = "option=getEnabled", produces = "application/json;charset=UTF-8")
    public String getEnabled() throws Exception {

        boolean globalEnable = net.apachegui.history.History.getGlobalEnable();

        JSONObject result = new JSONObject();

        VirtualHost enabledVirtualHosts[] = net.apachegui.history.History.getEnabledHosts();

        JSONArray allEnabled = new JSONArray();
        for (VirtualHost virtualHost : enabledVirtualHosts) {
            allEnabled.put(new JSONObject(virtualHost.toJSON()));
        }
        result.put("enabled", allEnabled);

        JSONArray globalHosts = new JSONArray();
        if (globalEnable) {
            VirtualHost globalVirtualHosts[] = net.apachegui.history.History.getGlobalHosts();

            for (VirtualHost virtualHost : globalVirtualHosts) {
                globalHosts.put(new JSONObject(virtualHost.toJSON()));
            }
        }
        result.put("global", globalHosts);
        result.put("globalEnable", globalEnable);

        return result.toString();
    }

    @RequestMapping(method = RequestMethod.GET, params = "option=getDisabled", produces = "application/json;charset=UTF-8")
    public String getDisabled() throws Exception {

        boolean globalEnable = net.apachegui.history.History.getGlobalEnable();

        JSONObject result = new JSONObject();

        VirtualHost disabledVirtualHosts[] = net.apachegui.history.History.getDisabledHosts();
        JSONArray allDisabled = new JSONArray();
        for (VirtualHost virtualHost : disabledVirtualHosts) {
            allDisabled.put(new JSONObject(virtualHost.toJSON()));
        }
        result.put("disabled", allDisabled);

        JSONArray globalHosts = new JSONArray();
        if (!globalEnable) {
            VirtualHost globalVirtualHosts[] = net.apachegui.history.History.getGlobalHosts();

            for (VirtualHost virtualHost : globalVirtualHosts) {
                globalHosts.put(new JSONObject(virtualHost.toJSON()));
            }
        }
        result.put("global", globalHosts);
        result.put("globalEnable", globalEnable);

        return result.toString();
    }

    @RequestMapping(method = RequestMethod.POST, params = "option=updateGlobal", produces = "application/json;charset=UTF-8")
    public String updateGlobal(@RequestParam(value = "type") String type) throws Exception {

        if (type.equals("enable")) {
            net.apachegui.history.History.globalEnable();
        }

        if (type.equals("disable")) {
            net.apachegui.history.History.globalDisable();
        }

        if (net.apachegui.server.Control.isServerRunning()) {
            String error = "";
            try {
                error = net.apachegui.server.Control.restartServer();
                if (!net.apachegui.server.Control.isServerRunning()) {
                    throw new Exception("The server could not restart");
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                net.apachegui.history.History.globalEnable();
                if (type.equals("enable")) {
                    net.apachegui.history.History.globalDisable();
                }

                if (type.equals("disable")) {
                    net.apachegui.history.History.globalEnable();
                }

                throw new Exception("There was an error while trying to restart the server, the changes were reverted: " + error + " " + e.getMessage());
            }
        }

        JSONObject result = new JSONObject();
        result.put("result", "success");

        return result.toString();
    }

    @RequestMapping(value = "/update", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    public String updateNonGlobal(@RequestBody String jsonString) throws Exception {

        JSONObject request = new JSONObject(jsonString);
        String option = request.getString("option");

        JSONArray hosts = request.getJSONArray("hosts");
        VirtualHost serverVirtualHosts[];
        if (option.equals("enable")) {
            serverVirtualHosts = net.apachegui.history.History.getDisabledHosts();
        } else {
            serverVirtualHosts = net.apachegui.history.History.getEnabledHosts();
        }

        ArrayList<VirtualHost> affectedHosts = new ArrayList<VirtualHost>();
        for (int i = 0; i < serverVirtualHosts.length; i++) {
            for (int j = 0; j < hosts.length(); j++) {

                if (serverVirtualHosts[i].getServerName().equals(hosts.getJSONObject(j).getString("ServerName"))) {

                    NetworkInfo[] serverNetworkInfo = serverVirtualHosts[i].getNetworkInfo();
                    JSONArray clientNetworkInfo = hosts.getJSONObject(j).getJSONArray("NetworkInfo");

                    boolean foundNetworkInfo = true;

                    OUTER: for (int k = 0; k < serverNetworkInfo.length && foundNetworkInfo; k++) {
                        foundNetworkInfo = false;

                        for (int l = 0; l < clientNetworkInfo.length(); l++) {

                            JSONObject currClientNetworkInfo = clientNetworkInfo.getJSONObject(l);
                            NetworkInfo cmpInfo = new NetworkInfo(currClientNetworkInfo.getInt("port"), currClientNetworkInfo.getString("address"));

                            if (serverNetworkInfo[k].equals(cmpInfo)) {
                                foundNetworkInfo = true;
                                continue OUTER;
                            }
                        }
                    }

                    if (foundNetworkInfo) {
                        affectedHosts.add(serverVirtualHosts[i]);

                        if (option.equals("enable")) {
                            net.apachegui.history.History.enable(serverVirtualHosts[i]);
                            for (int k = 0; k < serverVirtualHosts.length; k++) {
                                serverVirtualHosts[k].setLineOfStart(serverVirtualHosts[k].getLineOfStart() + 3);
                                serverVirtualHosts[k].setLineOfEnd(serverVirtualHosts[k].getLineOfEnd() + 3);
                            }

                        } else {
                            net.apachegui.history.History.disable(serverVirtualHosts[i]);

                            for (int k = 0; k < serverVirtualHosts.length; k++) {
                                serverVirtualHosts[k].setLineOfStart(serverVirtualHosts[k].getLineOfStart() - 3);
                                serverVirtualHosts[k].setLineOfEnd(serverVirtualHosts[k].getLineOfEnd() - 3);
                            }
                        }
                    }

                }
            }
        }

        if (net.apachegui.server.Control.isServerRunning()) {
            String error = "";
            try {
                error = net.apachegui.server.Control.restartServer();
                if (!net.apachegui.server.Control.isServerRunning()) {
                    throw new Exception("The server could not restart");
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                if (option.equals("enable")) {
                    for (int i = 0; i < affectedHosts.size(); i++) {
                        net.apachegui.history.History.disable(affectedHosts.get(i));
                    }

                } else {
                    for (int i = 0; i < affectedHosts.size(); i++) {
                        net.apachegui.history.History.enable(affectedHosts.get(i));
                    }
                }

                throw new Exception("There was an error while trying to restart the server, the changes were reverted: " + error + " " + e.getMessage());
            }
        }

        JSONObject result = new JSONObject();
        result.put("result", "success");

        return result.toString();

    }

}