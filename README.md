# agent-fi-relationship

[![Build Status](https://travis-ci.org/hmrc/agent-fi-relationship.svg)](https://travis-ci.org/hmrc/agent-fi-relationship) [ ![Download](https://api.bintray.com/packages/hmrc/releases/agent-fi-relationship/images/download.svg) ](https://bintray.com/hmrc/releases/agent-fi-relationship/_latestVersion)

This is a backend microservice for managing relationships between agent and client for the PERSONAL-INCOME-RECORD service.
It allows you to retrieve information about agent-client relationships, create and delete. This service has an isolated relationship store from other
supported services (ITSA and VAT), which it uses to manage relationships rather than calling a DES API. 


## Run the application locally

To run the application execute
```
sbt run
```

## Run the application via Service Manager

```sm --start AGENT_FI_ALL```


## Endpoints <a name="endpoints"></a>

Running locally, the services will run on http://localhost:9427

#### Create Relationship
```
POST   	/agent-fi-relationship/relationships/agent/:arn/service/:service/client/:clientId
```

Response Code(s)

| Status Code | Description |
|---|---|
| 201 | Relationship created |

#### View Relationship
```
GET   	/agent-fi-relationship/relationships/agent/:arn/service/:service/client/:clientId
```

Result
```
[
  {
    "arn": "AARN123",
    "service": "service123",
    "clientId": "clientId123",
    "relationshipStatus": "Active"
  }
]
```

Response Code(s)

| Status Code | Description |
|---|---|
| 201 | Relationship found and returned |
| 404 | Relationship not found |

#### View Relationships
```
GET           /agent-fi-relationship/relationships/service/:service/clientId/:clientId
```

Result
```
[
  {
    "service": "service123",
    "clientId": "clientId123",
    "relationshipStatus": "Active"
  },
  {...}
]
```

Response Code(s)

| Status Code | Description |
|---|---|
| 201 | Relationship found and returned |
| 404 | Relationship not found |

#### Delete Relationship
```
DELETE   	/agent-fi-relationship/relationships/agent/:arn/service/:service/client/:clientId
```

Response Code(s)

| Status Code | Description |
|---|---|
| 200 | Relationship deleted |

#### Delete Relationships
```
DELETE        /agent-fi-relationship/relationships/service/:service/clientId/:clientId
```

Response Code(s)

| Status Code | Description |
|---|---|
| 200 | Relationships deleted |

#### Access Control - View Relationship for Afi
```
GET   	/agent-fi-relationship/relationships/afi/agent/:arn/client/:clientId
```

Result
```
[
  {
    "arn": "AAABBB111222",
    "service": "afi",
    "clientId": "123456",
    "relationshipStatus": "Active"
  }
]
```

Response Code(s)

| Status Code | Description |
|---|---|
| 201 | Relationship found |
| 404 | Relationship not found |

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")