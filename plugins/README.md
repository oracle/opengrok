# Authorization plugins

This directory contains various authorization plugins:

  - FalsePlugin - denies everything
  - TruePlugin - allows everything
  - HttpBasicAuthorizationPlugin - sample plugin to utilize HTTP Basic auth
  - LdapPlugin - set of plugins to perform authorization based on LDAP
  - UserPlugin - extract user information from HTTP headers
    - this plugin can have multiple header decoders, the default is for Oracle SSO

## Debugging

In general, it should be possible to increase log level in Tomcat's
`logging.properties` file to get more verbose logging.

### UserPlugin

Has a special property called "fake" that allows to insert custom headers
with the "fake-" prefix that would be evaluated instead of the usual SSO headers.

Header insertion can be done e.g. using the Modify headers Firefox plugin.


```xml
        <!-- get user cred from HTTP headers -->
        <void method="add">
            <object class="org.opengrok.indexer.authorization.AuthorizationPlugin">
                <void property="name">
                    <string>opengrok.auth.plugin.UserPlugin</string>
                </void>
                <void property="flag">
                    <string>REQUISITE</string>
                </void>

                <!-- set fake parameter to true to allow insertion of custom headers -->
                <void property="setup">
                        <void method="put">
                                <string>fake</string>
                                <boolean>true</boolean>
                        </void>
                </void>
            </object>
        </void>

```

## Example configuration

The following snippet configures global authorization stack with 2 REQUISITE
plugins and a sub-stack with 1 SUFFICIENT and 1 REQUIRED plugin.

There is a config file `ldap-plugin-config.xml` specified globally that will be
used by LdapPlugin. See LdapPlugin directory for sample of this config file.

This snippet can be put info read-only configuration that is passed to the
indexer via the -R option.


```xml
   <!-- Authorization config begin -->

   <void property="pluginStack">
        <!-- The setup will be inherited to all sub-stacks -->
        <void property="setup">
            <void method="put">
                <string>configuration</string>
                <string>/opengrok/auth/config/ldap-plugin-config.xml</string>
            </void>
        </void>

        <void property="stack">
            <!-- get user cred from HTTP headers -->
            <void method="add">
                <object class="org.opengrok.indexer.authorization.AuthorizationPlugin">
                    <void property="name">
                        <string>opengrok.auth.plugin.UserPlugin</string>
                    </void>
                    <void property="flag">
                        <string>REQUISITE</string>
                    </void>
                </object>
            </void>

            <!-- get email, ou and uid -->
            <void method="add">
                <object class="org.opengrok.indexer.authorization.AuthorizationPlugin">
                    <void property="name">
                        <string>opengrok.auth.plugin.LdapUserPlugin</string>
                    </void>
                    <void property="flag">
                        <string>REQUISITE</string>
                    </void>
                </object>
    	        <void property="setup">
                    <void method="put">
                        <string>objectclass</string>
                        <string>posixAccount</string>
                    </void>
                </void>
            </void>

            <!-- Authorization stacks follow -->

            <void method="add">
                <object class="org.opengrok.indexer.authorization.AuthorizationStack">
                    <void property="forProjects">
                        <void method="add">
                            <string>foo</string>
                        </void>
                    </void>
                    <void property="forGroups">
                        <void method="add">
                            <string>mygroup</string>
                        </void>
                    </void>
                    <void property="name">
                        <string>substack for some source code</string>
                    </void>
                    <void property="flag">
                        <string>REQUIRED</string>
                    </void>
                    <void property="stack">
                        <void method="add">
                            <object class="org.opengrok.indexer.authorization.AuthorizationPlugin">
                                <void property="name">
                                    <string>opengrok.auth.plugin.LdapAttrPlugin</string>
                                </void>
                                <void property="flag">
                                    <string>SUFFICIENT</string>
                                </void>
                                <void property="setup">
                                    <void method="put">
                                        <string>attribute</string>
                                        <string>mail</string>
                                    </void>
                                    <void method="put">
                                        <string>file</string>
                                        <string>/opengrok/auth/config/whitelists/mycode-whitelist-mail.txt</string>
                                    </void>
                                </void>
                            </object>
                        </void>
                        <void method="add">
                            <object class="org.opengrok.indexer.authorization.AuthorizationPlugin">
                                <void property="name">
                                    <string>opengrok.auth.plugin.LdapFilterPlugin</string>
                                </void>
                                <void property="flag">
                                    <string>REQUIRED</string>
                                </void>
                                <void property="setup">
                                    <void method="put">
                                        <string>filter</string>
                                        <string>(&amp;(objectclass=posixGroup)(cn=my_src*)(memberUid=%uid%))</string>
                                    </void>
                                </void>
                            </object>
                        </void>
                    </void>
                </object>
            </void>
        </void>

   <!-- Authorization config end -->
   </object>
```

