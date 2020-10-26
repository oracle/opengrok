/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.analysis.powershell;

import java.util.HashSet;
import java.util.Set;

/**
 * PowerShell keyword hash.
 */
public class Consts {

    public static final Set<String> poshkwd = new HashSet<>();
    static {
        // Powershell keywords
        poshkwd.add("begin");
        poshkwd.add("break");
        poshkwd.add("catch");
        poshkwd.add("continue");
        poshkwd.add("data");
        poshkwd.add("default");     // for switch statement
        poshkwd.add("do");
        poshkwd.add("dynamicparam");
        poshkwd.add("else");
        poshkwd.add("elseif");
        poshkwd.add("end");
        poshkwd.add("exit");
        poshkwd.add("filter");
        poshkwd.add("finally");
        poshkwd.add("for");
        poshkwd.add("foreach");
        poshkwd.add("function");
        poshkwd.add("if");
        poshkwd.add("in");
        poshkwd.add("inlinescript");
        poshkwd.add("parallel");
        poshkwd.add("param");
        poshkwd.add("process");
        poshkwd.add("return");
        poshkwd.add("sequence");
        poshkwd.add("jobs");
        poshkwd.add("switch");
        poshkwd.add("throw");
        poshkwd.add("trap");
        poshkwd.add("try");
        poshkwd.add("until");
        poshkwd.add("while");
        poshkwd.add("workgroup");
        
        // comparison operators
        poshkwd.add("-eq");
        poshkwd.add("-ne");
        poshkwd.add("-gt");
        poshkwd.add("-lt");
        poshkwd.add("-le");
        poshkwd.add("-ge");
        poshkwd.add("-match");
        poshkwd.add("-notmatch");
        poshkwd.add("-replace");
        poshkwd.add("-like");
        poshkwd.add("-notlike");
        
        // logical operators
        poshkwd.add("-and");
        poshkwd.add("-or");
        poshkwd.add("-xor");
        poshkwd.add("-not");
        poshkwd.add("-band");
        poshkwd.add("-bor");
        poshkwd.add("-bxor");
        poshkwd.add("-bnot");
        
        // type operators
        poshkwd.add("-is");
        poshkwd.add("-isnot");
        poshkwd.add("-as");
        
        // miscellaneous operators
        poshkwd.add("-split");
        poshkwd.add("-join");
        poshkwd.add("-f");
        
        // constants
        poshkwd.add("true");
        poshkwd.add("false");
        poshkwd.add("null");
        poshkwd.add("_");    // $_ really

        // miscellaneous
        poshkwd.add("any");
        poshkwd.add("leaf");
        poshkwd.add("container");
        
        // some standard aliases to cmdlets
        poshkwd.add("cat");
        poshkwd.add("cp");
        poshkwd.add("chdir");
        poshkwd.add("compare");
        poshkwd.add("copy");
        poshkwd.add("del");
        poshkwd.add("diff");
        poshkwd.add("dir");
        poshkwd.add("echo");
        poshkwd.add("kill");
        poshkwd.add("lp");
        poshkwd.add("man");
        poshkwd.add("measure");
        poshkwd.add("mount");
        poshkwd.add("move");
        poshkwd.add("mv");
        poshkwd.add("popd");
        poshkwd.add("pushd");
        poshkwd.add("rmdir");
        poshkwd.add("select");
        poshkwd.add("set");
        poshkwd.add("sleep");
        poshkwd.add("sort");
        poshkwd.add("start");
        poshkwd.add("tee");
        poshkwd.add("type");
        poshkwd.add("where");
        poshkwd.add("write");
        
        // Cmdlets
        poshkwd.add("add-bitsfile");
        poshkwd.add("add-computer");
        poshkwd.add("add-content");
        poshkwd.add("add-history");
        poshkwd.add("add-jobtrigger");
        poshkwd.add("add-member");
        poshkwd.add("add-pssnapin");
        poshkwd.add("add-type");
        poshkwd.add("checkpoint-computer");
        poshkwd.add("clear-content");
        poshkwd.add("clear-eventlog");
        poshkwd.add("clear-history");
        poshkwd.add("clear-item");
        poshkwd.add("clear-itemproperty");
        poshkwd.add("clear-recyclebin");
        poshkwd.add("clear-variable");
        poshkwd.add("compare-object");
        poshkwd.add("complete-bitstransfer");
        poshkwd.add("complete-transaction");
        poshkwd.add("compress-archive");
        poshkwd.add("configuration");
        poshkwd.add("connect-pssession");
        poshkwd.add("connect-wsman");
        poshkwd.add("convert-path");
        poshkwd.add("convert-string");
        poshkwd.add("convertfrom-csv");
        poshkwd.add("convertfrom-json");
        poshkwd.add("convertfrom-sddlstring");
        poshkwd.add("convertfrom-securestring");
        poshkwd.add("convertfrom-string");
        poshkwd.add("convertfrom-stringdata");
        poshkwd.add("convertto-csv");
        poshkwd.add("convertto-html");
        poshkwd.add("convertto-json");
        poshkwd.add("convertto-securestring");
        poshkwd.add("convertto-xml");
        poshkwd.add("copy-item");
        poshkwd.add("copy-itemproperty");
        poshkwd.add("debug-job");
        poshkwd.add("debug-process");
        poshkwd.add("debug-runspace");
        poshkwd.add("disable-computerrestore");
        poshkwd.add("disable-dscdebug");
        poshkwd.add("disable-jobtrigger");
        poshkwd.add("disable-networkswitchethernetport");
        poshkwd.add("disable-networkswitchfeature");
        poshkwd.add("disable-networkswitchvlan");
        poshkwd.add("disable-psbreakpoint");
        poshkwd.add("disable-psremoting");
        poshkwd.add("disable-pssessionconfiguration");
        poshkwd.add("disable-pstrace");
        poshkwd.add("disable-pswsmancombinedtrace");
        poshkwd.add("disable-runspacedebug");
        poshkwd.add("disable-scheduledjob");
        poshkwd.add("disable-wsmancredssp");
        poshkwd.add("disable-wsmantrace");
        poshkwd.add("disconnect-pssession");
        poshkwd.add("disconnect-wsman");
        poshkwd.add("enable-computerrestore");
        poshkwd.add("enable-dscdebug");
        poshkwd.add("enable-jobtrigger");
        poshkwd.add("enable-networkswitchethernetport");
        poshkwd.add("enable-networkswitchfeature");
        poshkwd.add("enable-networkswitchvlan");
        poshkwd.add("enable-psbreakpoint");
        poshkwd.add("enable-psremoting");
        poshkwd.add("enable-pssessionconfiguration");
        poshkwd.add("enable-pstrace");
        poshkwd.add("enable-pswsmancombinedtrace");
        poshkwd.add("enable-runspacedebug");
        poshkwd.add("enable-scheduledjob");
        poshkwd.add("enable-wsmancredssp");
        poshkwd.add("enable-wsmantrace");
        poshkwd.add("enter-pshostprocess");
        poshkwd.add("enter-pssession");
        poshkwd.add("exit-pshostprocess");
        poshkwd.add("exit-pssession");
        poshkwd.add("expand-archive");
        poshkwd.add("export-alias");
        poshkwd.add("export-binarymilog");
        poshkwd.add("export-clixml");
        poshkwd.add("export-console");
        poshkwd.add("export-counter");
        poshkwd.add("export-csv");
        poshkwd.add("export-formatdata");
        poshkwd.add("export-modulemember");
        poshkwd.add("export-odataendpointproxy");
        poshkwd.add("export-pssession");
        poshkwd.add("find-dscresource");
        poshkwd.add("find-module");
        poshkwd.add("find-package");
        poshkwd.add("find-packageprovider");
        poshkwd.add("find-script");
        poshkwd.add("foreach-object");
        poshkwd.add("format-custom");
        poshkwd.add("format-hex");
        poshkwd.add("format-list");
        poshkwd.add("format-table");
        poshkwd.add("format-wide");
        poshkwd.add("get-acl");
        poshkwd.add("get-alias");
        poshkwd.add("get-applockerfileinformation");
        poshkwd.add("get-applockerpolicy");
        poshkwd.add("get-authenticodesignature");
        poshkwd.add("get-bitstransfer");
        poshkwd.add("get-childitem");
        poshkwd.add("get-cimassociatedinstance");
        poshkwd.add("get-cimclass");
        poshkwd.add("get-ciminstance");
        poshkwd.add("get-cimsession");
        poshkwd.add("get-clipboard");
        poshkwd.add("get-cmsmessage");
        poshkwd.add("get-command");
        poshkwd.add("get-computerrestorepoint");
        poshkwd.add("get-content");
        poshkwd.add("get-controlpanelitem");
        poshkwd.add("get-counter");
        poshkwd.add("get-credential");
        poshkwd.add("get-culture");
        poshkwd.add("get-date");
        poshkwd.add("get-dscconfiguration");
        poshkwd.add("get-dscconfigurationstatus");
        poshkwd.add("get-dsclocalconfigurationmanager");
        poshkwd.add("get-dscresource");
        poshkwd.add("get-event");
        poshkwd.add("get-eventlog");
        poshkwd.add("get-eventsubscriber");
        poshkwd.add("get-executionpolicy");
        poshkwd.add("get-filehash");
        poshkwd.add("get-formatdata");
        poshkwd.add("get-help");
        poshkwd.add("get-history");
        poshkwd.add("get-host");
        poshkwd.add("get-hotfix");
        poshkwd.add("get-installedmodule");
        poshkwd.add("get-installedscript");
        poshkwd.add("get-isesnippet");
        poshkwd.add("get-item");
        poshkwd.add("get-itemproperty");
        poshkwd.add("get-itempropertyvalue");
        poshkwd.add("get-job");
        poshkwd.add("get-jobtrigger");
        poshkwd.add("get-location");
        poshkwd.add("get-logproperties");
        poshkwd.add("get-member");
        poshkwd.add("get-module");
        poshkwd.add("get-networkswitchethernetport");
        poshkwd.add("get-networkswitchfeature");
        poshkwd.add("get-networkswitchglobaldata");
        poshkwd.add("get-networkswitchvlan");
        poshkwd.add("get-psbreakpoint");
        poshkwd.add("get-pscallstack");
        poshkwd.add("get-psdrive");
        poshkwd.add("get-pshostprocessinfo");
        poshkwd.add("get-psprovider");
        poshkwd.add("get-psrepository");
        poshkwd.add("get-pssession");
        poshkwd.add("get-pssessioncapability");
        poshkwd.add("get-pssessionconfiguration");
        poshkwd.add("get-pssnapin");
        poshkwd.add("get-package");
        poshkwd.add("get-packageprovider");
        poshkwd.add("get-packagesource");
        poshkwd.add("get-pfxcertificate");
        poshkwd.add("get-process");
        poshkwd.add("get-random");
        poshkwd.add("get-runspace");
        poshkwd.add("get-runspacedebug");
        poshkwd.add("get-scheduledjob");
        poshkwd.add("get-scheduledjoboption");
        poshkwd.add("get-service");
        poshkwd.add("get-tracesource");
        poshkwd.add("get-transaction");
        poshkwd.add("get-troubleshootingpack");
        poshkwd.add("get-typedata");
        poshkwd.add("get-uiculture");
        poshkwd.add("get-unique");
        poshkwd.add("get-variable");
        poshkwd.add("get-wsmancredssp");
        poshkwd.add("get-wsmaninstance");
        poshkwd.add("get-winevent");
        poshkwd.add("get-wmiobject");
        poshkwd.add("group-object");
        poshkwd.add("import-alias");
        poshkwd.add("import-binarymilog");
        poshkwd.add("import-clixml");
        poshkwd.add("import-counter");
        poshkwd.add("import-csv");
        poshkwd.add("import-isesnippet");
        poshkwd.add("import-localizeddata");
        poshkwd.add("import-module");
        poshkwd.add("import-pssession");
        poshkwd.add("import-packageprovider");
        poshkwd.add("import-powershelldatafile");
        poshkwd.add("install-module");
        poshkwd.add("install-package");
        poshkwd.add("install-packageprovider");
        poshkwd.add("install-script");
        poshkwd.add("invoke-asworkflow");
        poshkwd.add("invoke-cimmethod");
        poshkwd.add("invoke-command");
        poshkwd.add("invoke-dscresource");
        poshkwd.add("invoke-expression");
        poshkwd.add("invoke-history");
        poshkwd.add("invoke-item");
        poshkwd.add("invoke-restmethod");
        poshkwd.add("invoke-troubleshootingpack");
        poshkwd.add("invoke-wsmanaction");
        poshkwd.add("invoke-webrequest");
        poshkwd.add("invoke-wmimethod");
        poshkwd.add("join-path");
        poshkwd.add("limit-eventlog");
        poshkwd.add("measure-command");
        poshkwd.add("measure-object");
        poshkwd.add("move-item");
        poshkwd.add("move-itemproperty");
        poshkwd.add("new-alias");
        poshkwd.add("new-applockerpolicy");
        poshkwd.add("new-ciminstance");
        poshkwd.add("new-cimsession");
        poshkwd.add("new-cimsessionoption");
        poshkwd.add("new-dscchecksum");
        poshkwd.add("new-event");
        poshkwd.add("new-eventlog");
        poshkwd.add("new-guid");
        poshkwd.add("new-isesnippet");
        poshkwd.add("new-item");
        poshkwd.add("new-itemproperty");
        poshkwd.add("new-jobtrigger");
        poshkwd.add("new-module");
        poshkwd.add("new-modulemanifest");
        poshkwd.add("new-networkswitchvlan");
        poshkwd.add("new-object");
        poshkwd.add("new-psdrive");
        poshkwd.add("new-psrolecapabilityfile");
        poshkwd.add("new-pssession");
        poshkwd.add("new-pssessionconfigurationfile");
        poshkwd.add("new-pssessionoption");
        poshkwd.add("new-pstransportoption");
        poshkwd.add("new-psworkflowexecutionoption");
        poshkwd.add("new-psworkflowsession");
        poshkwd.add("new-scheduledjoboption");
        poshkwd.add("new-scriptfileinfo");
        poshkwd.add("new-service");
        poshkwd.add("new-temporaryfile");
        poshkwd.add("new-timespan");
        poshkwd.add("new-variable");
        poshkwd.add("new-wsmaninstance");
        poshkwd.add("new-wsmansessionoption");
        poshkwd.add("new-webserviceproxy");
        poshkwd.add("new-winevent");
        poshkwd.add("out-default");
        poshkwd.add("out-file");
        poshkwd.add("out-gridview");
        poshkwd.add("out-host");
        poshkwd.add("out-null");
        poshkwd.add("out-printer");
        poshkwd.add("out-string");
        poshkwd.add("pop-location");
        poshkwd.add("protect-cmsmessage");
        poshkwd.add("publish-dscconfiguration");
        poshkwd.add("publish-module");
        poshkwd.add("publish-script");
        poshkwd.add("push-location");
        poshkwd.add("read-host");
        poshkwd.add("receive-job");
        poshkwd.add("receive-pssession");
        poshkwd.add("register-argumentcompleter");
        poshkwd.add("register-cimindicationevent");
        poshkwd.add("register-engineevent");
        poshkwd.add("register-objectevent");
        poshkwd.add("register-psrepository");
        poshkwd.add("register-pssessionconfiguration");
        poshkwd.add("register-packagesource");
        poshkwd.add("register-scheduledjob");
        poshkwd.add("register-wmievent");
        poshkwd.add("remove-bitstransfer");
        poshkwd.add("remove-ciminstance");
        poshkwd.add("remove-cimsession");
        poshkwd.add("remove-computer");
        poshkwd.add("remove-dscconfigurationdocument");
        poshkwd.add("remove-event");
        poshkwd.add("remove-eventlog");
        poshkwd.add("remove-item");
        poshkwd.add("remove-itemproperty");
        poshkwd.add("remove-job");
        poshkwd.add("remove-jobtrigger");
        poshkwd.add("remove-module");
        poshkwd.add("remove-networkswitchethernetportipaddress");
        poshkwd.add("remove-networkswitchvlan");
        poshkwd.add("remove-psbreakpoint");
        poshkwd.add("remove-psdrive");
        poshkwd.add("remove-pssession");
        poshkwd.add("remove-pssnapin");
        poshkwd.add("remove-typedata");
        poshkwd.add("remove-variable");
        poshkwd.add("remove-wsmaninstance");
        poshkwd.add("remove-wmiobject");
        poshkwd.add("rename-computer");
        poshkwd.add("rename-item");
        poshkwd.add("rename-itemproperty");
        poshkwd.add("reset-computermachinepassword");
        poshkwd.add("resolve-path");
        poshkwd.add("restart-computer");
        poshkwd.add("restart-service");
        poshkwd.add("restore-computer");
        poshkwd.add("restore-dscconfiguration");
        poshkwd.add("restore-networkswitchconfiguration");
        poshkwd.add("resume-bitstransfer");
        poshkwd.add("resume-job");
        poshkwd.add("resume-service");
        poshkwd.add("save-help");
        poshkwd.add("save-module");
        poshkwd.add("save-networkswitchconfiguration");
        poshkwd.add("save-package");
        poshkwd.add("save-script");
        poshkwd.add("select-object");
        poshkwd.add("select-string");
        poshkwd.add("select-xml");
        poshkwd.add("send-mailmessage");
        poshkwd.add("set-acl");
        poshkwd.add("set-alias");
        poshkwd.add("set-applockerpolicy");
        poshkwd.add("set-authenticodesignature");
        poshkwd.add("set-bitstransfer");
        poshkwd.add("set-ciminstance");
        poshkwd.add("set-clipboard");
        poshkwd.add("set-content");
        poshkwd.add("set-date");
        poshkwd.add("set-dsclocalconfigurationmanager");
        poshkwd.add("set-executionpolicy");
        poshkwd.add("set-item");
        poshkwd.add("set-itemproperty");
        poshkwd.add("set-jobtrigger");
        poshkwd.add("set-location");
        poshkwd.add("set-logproperties");
        poshkwd.add("set-networkswitchethernetportipaddress");
        poshkwd.add("set-networkswitchportmode");
        poshkwd.add("set-networkswitchportproperty");
        poshkwd.add("set-networkswitchvlanproperty");
        poshkwd.add("set-psbreakpoint");
        poshkwd.add("set-psdebug");
        poshkwd.add("set-psrepository");
        poshkwd.add("set-pssessionconfiguration");
        poshkwd.add("set-packagesource");
        poshkwd.add("set-scheduledjob");
        poshkwd.add("set-scheduledjoboption");
        poshkwd.add("set-service");
        poshkwd.add("set-strictmode");
        poshkwd.add("set-tracesource");
        poshkwd.add("set-variable");
        poshkwd.add("set-wsmaninstance");
        poshkwd.add("set-wsmanquickconfig");
        poshkwd.add("set-wmiinstance");
        poshkwd.add("show-command");
        poshkwd.add("show-controlpanelitem");
        poshkwd.add("show-eventlog");
        poshkwd.add("sort-object");
        poshkwd.add("split-path");
        poshkwd.add("start-bitstransfer");
        poshkwd.add("start-dscconfiguration");
        poshkwd.add("start-job");
        poshkwd.add("start-process");
        poshkwd.add("start-service");
        poshkwd.add("start-sleep");
        poshkwd.add("start-trace");
        poshkwd.add("start-transaction");
        poshkwd.add("start-transcript");
        poshkwd.add("stop-computer");
        poshkwd.add("stop-dscconfiguration");
        poshkwd.add("stop-job");
        poshkwd.add("stop-process");
        poshkwd.add("stop-service");
        poshkwd.add("stop-trace");
        poshkwd.add("stop-transcript");
        poshkwd.add("suspend-bitstransfer");
        poshkwd.add("suspend-job");
        poshkwd.add("suspend-service");
        poshkwd.add("tee-object");
        poshkwd.add("test-applockerpolicy");
        poshkwd.add("test-computersecurechannel");
        poshkwd.add("test-connection");
        poshkwd.add("test-dscconfiguration");
        poshkwd.add("test-modulemanifest");
        poshkwd.add("test-pssessionconfigurationfile");
        poshkwd.add("test-path");
        poshkwd.add("test-scriptfileinfo");
        poshkwd.add("test-wsman");
        poshkwd.add("trace-command");
        poshkwd.add("unblock-file");
        poshkwd.add("undo-transaction");
        poshkwd.add("uninstall-module");
        poshkwd.add("uninstall-package");
        poshkwd.add("uninstall-script");
        poshkwd.add("unprotect-cmsmessage");
        poshkwd.add("unregister-event");
        poshkwd.add("unregister-psrepository");
        poshkwd.add("unregister-pssessionconfiguration");
        poshkwd.add("unregister-packagesource");
        poshkwd.add("unregister-scheduledjob");
        poshkwd.add("update-dscconfiguration");
        poshkwd.add("update-formatdata");
        poshkwd.add("update-help");
        poshkwd.add("update-list");
        poshkwd.add("update-module");
        poshkwd.add("update-modulemanifest");
        poshkwd.add("update-script");
        poshkwd.add("update-scriptfileinfo");
        poshkwd.add("update-typedata");
        poshkwd.add("use-transaction");
        poshkwd.add("wait-debugger");
        poshkwd.add("wait-event");
        poshkwd.add("wait-job");
        poshkwd.add("wait-process");
        poshkwd.add("where-object");
        poshkwd.add("write-debug");
        poshkwd.add("write-error");
        poshkwd.add("write-eventlog");
        poshkwd.add("write-host");
        poshkwd.add("write-information");
        poshkwd.add("write-output");
        poshkwd.add("write-progress");
        poshkwd.add("write-verbose");
        poshkwd.add("write-warning");

        poshkwd.add("bool"); // Built-In Types Table (C# Reference)
        poshkwd.add("boolean"); // Built-In Types Table (C# Reference)
        poshkwd.add("byte"); // Built-In Types Table (C# Reference)
        poshkwd.add("char"); // Built-In Types Table (C# Reference)
        poshkwd.add("decimal"); // Built-In Types Table (C# Reference)
        poshkwd.add("double"); // Built-In Types Table (C# Reference)
        poshkwd.add("float"); // Built-In Types Table (C# Reference)
        poshkwd.add("int"); // Built-In Types Table (C# Reference)
        poshkwd.add("int16"); // Built-In Types Table (C# Reference)
        poshkwd.add("int32"); // Built-In Types Table (C# Reference)
        poshkwd.add("int64"); // Built-In Types Table (C# Reference)
        poshkwd.add("long"); // Built-In Types Table (C# Reference)
        poshkwd.add("object"); // Built-In Types Table (C# Reference)
        poshkwd.add("sbyte"); // Built-In Types Table (C# Reference)
        poshkwd.add("short"); // Built-In Types Table (C# Reference)
        poshkwd.add("single"); // Built-In Types Table (C# Reference)
        poshkwd.add("string"); // Built-In Types Table (C# Reference)
        poshkwd.add("system"); // Built-In Types Table (C# Reference)
        poshkwd.add("uint"); // Built-In Types Table (C# Reference)
        poshkwd.add("uint16"); // Built-In Types Table (C# Reference)
        poshkwd.add("uint32"); // Built-In Types Table (C# Reference)
        poshkwd.add("uint64"); // Built-In Types Table (C# Reference)
        poshkwd.add("ulong"); // Built-In Types Table (C# Reference)
        poshkwd.add("ushort"); // Built-In Types Table (C# Reference)
    }

    private Consts() {
    }

}
