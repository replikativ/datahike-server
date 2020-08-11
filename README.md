# datahike-server

With Datahike Server you can run your Datahike as a server.

## Logging
We are using the [library taoensso.timbre by Peter Taoussanis]() to provide
meaningful log messages. Please set the loglevel that you prefer via means
of configuration below. The possible levels are sorted in order from least
severe to most severe:
- trace
- debug
- info
- warn
- error
- fatal
- report

## Configuring Datahike Server
### File Configuration
Datahike Server loads configuration from `resources/config.edn` relative to the
current directory. This file has a number of options and overwrites all other
configuration given via environment or properties. Below you can find an example
to configure both Datahike and the server.
```
{:datahike {:store {:backend :file
                    :path "/tmp/dh-2"}
            :schema-on-read true
            :temporal-index false}
 :server {:port 3000
          :join? false
          :loglevel :debug}}
```

### Configuration via Environment and Properties
Datahike Server can also be configured via environment variables and java system
properties. Please take a look at the [configuration of Datahike](https://github.com/replikativ/datahike/blob/development/doc/config.md) to get an
overview of the number of possible configuration options regarding the database.
To configure the server please see the options below. Like in Datahike they are
read via the [environ library by weavejester](https://github.com/weavejester/environ).
Please provide the logging level without colon. Beware that a configuration file
overwrites the values from environment and properties.

properties                  | envvar                   | default
----------------------------|--------------------------|-------------
datahike.server.port        | DATAHIKE_SERVER_PORT     | 3000
datahike.server.join        | DATAHIKE_SERVER_JOIN     | false
datahike.server.loglevel    | DATAHIKE_SERVER_LOGLEVEL | :info

## License

Copyright © 2020 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
