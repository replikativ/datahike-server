# 1. Record architecture decisions

Date: 2020-09-16

## Status

Accepted

## Context

We need an authentication mechanism to run Datahike Server in an accessible network to only give permission to certain users.

## Decision

Token authentication is a very simple but secure authentication mechanism. It is implemented with [Buddy](https://github.com/funcool/buddy-auth) and a token is set via configuration. Currently only one token can be set. Username/password authentication is another very simple authentication mechanism and can be implemented later without a lot of work. JWT as authentication is a lot more complex and seems not suitable for our use-case because Datahike is not running in multiple instances parallel currently so it is not important to have stateless authentication.

The advantage over username/password authentication is that the token can be created on a website in case of a Datahike service and the user can register and log in there with username and password. There a secure long enough token would be automatically generated and can be regenerated anytime. Even if the user uses a bad password and his account would be hacked there will be no way to read the secure token and the database might still be secure depending on the implementation of the regenerating of a new token.

## Consequences

Currently there is a dev-mode option for Datahike Server that a user can set via configuration and then there is no token needed. Of course this is only for development purposes. Once a token is set via configuration the user needs to authenticate via token-header otherwise a 401 will be returned. Communication needs to be protected with TLS.
