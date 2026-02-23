# nats-http

nats-http is a bridge utility that subscribes to a NATS subject and forwards
incoming messages to a specified HTTP endpoint.  It allows you to trigger
webhooks or internal HTTP services via NATS messaging.

## Example with httpbin.org

Create a configuration file, called `config.edn`:

```
cat <<EOF > config.edn
{"nats-http": {:scheme :https
               :server-name "httpbin.org"
               :server-port 443
               :uri "/post"
               :request-method :post
               :query-string "?q=someQueryString"
               :headers {:my-header-1 "header-value"
                         :my-header-2 ["a", "b"]}}}
EOF
```

Pass the configuration file's path as an argument to `nats-http:

```
nix run github:wagdav/nats-http -- config.edn
```

nats-http exposes a [NATS service][Services], which you can list with the NATS
CLI:

```
$ nats services ls
╭─────────────────────────────────────────────────────────────────╮
│                           All Services                          │
├───────────┬─────────┬────────────────────────┬──────────────────┤
│ Name      │ Version │ ID                     │ Description      │
├───────────┼─────────┼────────────────────────┼──────────────────┤
│ nats-http │ 0.0.1   │ ZyB38VzQ0TdkgYEezKpEPH │ NATS HTTP bridge │
╰───────────┴─────────┴────────────────────────┴──────────────────╯
```

When you call the service's http endpoint via NATS, the bridge sends an HTTP
request with the specified parameters:

```
nats req nats-http.http '{:body "hello from httpbin.org"}'
```

[Services]: https://docs.nats.io/using-nats/developer/services
