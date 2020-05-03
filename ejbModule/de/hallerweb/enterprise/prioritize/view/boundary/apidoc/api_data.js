define({ "api": [
  {
    "type": "get",
    "url": "/calendar/reservations",
    "title": "getTimeSpansForReservations",
    "name": "getTimeSpansForReservations",
    "group": "_calendar",
    "description": "<p>Searches for all resource reservations to resources (devices) within a department. The department is given by the departmentToken parameter. Parameters &quot;from&quot; and &quot;to&quot; indicate the the timespan to search.</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "departmentToken",
            "description": "<p>Department token to use.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "long",
            "optional": false,
            "field": "from",
            "description": "<p>Java timestamp to indicate the start date from which to search for resevations.</p>"
          },
          {
            "group": "Parameter",
            "type": "long",
            "optional": false,
            "field": "to",
            "description": "<p>Java timestamp to indicate the end date to search for resevations.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "TimeSpan",
            "optional": false,
            "field": "timespan",
            "description": "<p>JSON Objects with all timespans currently registered for reservations.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "    HTTP/1.1 200 OK\n\t[\n   {\n     \"id\" : 76,\n     \"title\" : \"aaaa\",\n     \"description\" : \"default:aaaa[admin]\",\n     \"dateFrom\" : 1479164400000,\n     \"dateUntil\" : 1485817200000,\n     \"type\" : \"RESOURCE_RESERVATION\",\n     \"department\" : ...list of departments...\n   }\n ]",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>DepartmentToken or APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./CalendarService.java",
    "groupTitle": "_calendar"
  },
  {
    "type": "get",
    "url": "/calendar/self",
    "title": "getTimeSpansForUser",
    "name": "getTimeSpansForUser",
    "group": "_calendar",
    "description": "<p>Searches for all Timespan entries for the user with the given apiKey. This includes resource reservations initiated by this user, illness and vacation entries.</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "TimeSpan",
            "optional": false,
            "field": "timespan",
            "description": "<p>JSON Objects with all timespans currently registered for the user.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "    HTTP/1.1 200 OK\n\t[\n   {\n     \"id\" : 76,\n     \"title\" : \"aaaa\",\n     \"description\" : \"default:aaaa[admin]\",\n     \"dateFrom\" : 1479164400000,\n     \"dateUntil\" : 1485817200000,\n     \"type\" : \"RESOURCE_RESERVATION\",\n     \"department\" : ...list of departments...\n   }\n ]",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>DepartmentToken or APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./CalendarService.java",
    "groupTitle": "_calendar"
  },
  {
    "type": "get",
    "url": "/companies/{id}",
    "title": "getCompany",
    "name": "getCompany",
    "group": "_company",
    "description": "<p>Returns the company with the given id.</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "Company",
            "optional": false,
            "field": "company",
            "description": "<p>JSON Object with the company of the given id.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "    HTTP/1.1 200 OK\n\t{\n   \"id\" : 1,\n   \"name\" : \"Default Company\",\n   \"description\" : \"\",\n   \"mainAddress\" : {\n   \"id\" : 7,\n   \"zipCode\" : \"00000\",\n   \"phone\" : \"00000-00000\",\n   \"fax\" : \"00000-00000\",\n   \"city\" : \"City of Admin\",\n   \"street\" : \"Street of Admins\"\n   ...many more\n }",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./CompanyService.java",
    "groupTitle": "_company"
  },
  {
    "type": "get",
    "url": "/search/departments",
    "title": "searchDepartments",
    "name": "searchDepartments",
    "group": "_company",
    "description": "<p>Searches all departments which contain the given phrase</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "phrase",
            "description": "<p>The search phrase used in the search.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "Department",
            "optional": false,
            "field": "department",
            "description": "<p>JSON department Objects which contained the search phrase.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "    HTTP/1.1 200 OK\n[\n  {\n    \"id\" : 6,\n    \"address\" : {\n    \"id\" : 5,\n    \"zipCode\" : \"00000\",\n    \"phone\" : \"00000-00000\",\n    \"fax\" : \"00000-00000\",\n    \"city\" : \"City of Admin\",\n    \"street\" : \"Street of Admins\"\n    ...many more\n   }\n]",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./CompanyService.java",
    "groupTitle": "_company"
  },
  {
    "type": "post",
    "url": "/create",
    "title": "createCounter",
    "name": "createCounter",
    "group": "_counters",
    "description": "<p>creates a counter</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "uuid",
            "description": "<p>the uuid of the counter to be created.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "name",
            "description": "<p>the name of the counter to be created.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "description",
            "description": "<p>the description of the counter to be created.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "initialValue",
            "description": "<p>the initial value (long) of the counter to be created.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "optional": false,
            "field": "IndustrieCounter",
            "description": "<p>JSON representation of the counter just created.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "    HTTP/1.1 200 OK\n{\n\"id\": 64,\n\"counter\": {\n\"id\": 63,\n\"nfcUnit\": {\n\"id\": 62,\n\"uuid\": \"49ee74df-9c9f-4410-8177-4b33095e939d\",\n\"name\": \"Counter\",\n\"description\": \"NFC counter\",\n\"payload\": \"1\",\n\"payloadSize\": 1,\n\"lastConnectedTime\": 1588453253120,\n\"latitude\": null,\n\"longitude\": null,\n\"sequenceNumber\": 0,\n\"unitType\": \"COUNTER\",\n\"lastConnectedDevice\": null,\n\"wrappedResource\": null\n},\n\"uuid\": \"49ee74df-9c9f-4410-8177-4b33095e939d\",\n\"value\": 1\n\t},\n\"name\": \"test\",\n\"description\": \"testcounter\",\n\"department\": null\n}",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./IndustrieCounterService.java",
    "groupTitle": "_counters"
  },
  {
    "type": "put",
    "url": "/uuid/{uuid}",
    "title": "editCounter",
    "name": "editCounter",
    "group": "_counters",
    "description": "<p>performs an increase, decrease or reset on the given counter</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "action",
            "description": "<ul> <li>on of increase, decrease or reset.</li> </ul>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "uuid",
            "description": "<p>the uuid of the counter to be retrieved.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "optional": false,
            "field": "IndustrieCounter",
            "description": "<p>JSON representation of the counter just edited.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "    HTTP/1.1 200 OK\n{\n\"id\": 64,\n\"counter\": {\n\"id\": 63,\n\"nfcUnit\": {\n\"id\": 62,\n\"uuid\": \"49ee74df-9c9f-4410-8177-4b33095e939d\",\n\"name\": \"Counter\",\n\"description\": \"NFC counter\",\n\"payload\": \"2\",\n\"payloadSize\": 1,\n\"lastConnectedTime\": 1588453253120,\n\"latitude\": null,\n\"longitude\": null,\n\"sequenceNumber\": 1,\n\"unitType\": \"COUNTER\",\n\"lastConnectedDevice\": null,\n\"wrappedResource\": null\n},\n\"uuid\": \"49ee74df-9c9f-4410-8177-4b33095e939d\",\n\"value\": 2\n\t},\n\"name\": \"test\",\n\"description\": \"testcounter\",\n\"department\": null\n}",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./IndustrieCounterService.java",
    "groupTitle": "_counters"
  },
  {
    "type": "get",
    "url": "/uuid/{uuid}",
    "title": "getIndustrieCounter",
    "name": "getIndustrieCounter",
    "group": "_counters",
    "description": "<p>deletes the message with the given id.</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "uuid",
            "description": "<p>the uuid of the counter to be retrieved.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "String",
            "optional": false,
            "field": "JSON",
            "description": "<p>object representing a counter.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "    HTTP/1.1 200 OK\n{\n\"id\": 64,\n\"counter\": {\n\"id\": 63,\n\"nfcUnit\": {\n\"id\": 62,\n\"uuid\": \"49ee74df-9c9f-4410-8177-4b33095e939d\",\n\"name\": \"Counter\",\n\"description\": \"NFC counter\",\n\"payload\": \"1\",\n\"payloadSize\": 1,\n\"lastConnectedTime\": 1588453253120,\n\"latitude\": null,\n\"longitude\": null,\n\"sequenceNumber\": 0,\n\"unitType\": \"COUNTER\",\n\"lastConnectedDevice\": null,\n\"wrappedResource\": null\n},\n\"uuid\": \"49ee74df-9c9f-4410-8177-4b33095e939d\",\n\"value\": 1\n\t},\n\"name\": \"test\",\n\"description\": \"testcounter\",\n\"department\": null\n}",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./IndustrieCounterService.java",
    "groupTitle": "_counters"
  },
  {
    "type": "get",
    "url": "/departments/{id}",
    "title": "getDepartment",
    "name": "getDepartment",
    "group": "_department",
    "description": "<p>Returns the department with the given id.</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "Department",
            "optional": false,
            "field": "company",
            "description": "<p>JSON Object with the department of the given id.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "HTTP/1.1 200 OK",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./DepartmentService.java",
    "groupTitle": "_department"
  },
  {
    "type": "delete",
    "url": "/remove?apiKey={apiKey}&departmentToken={departmenttoken}&id={id}",
    "title": "deleteDocument",
    "name": "deleteDocument",
    "group": "_documents",
    "description": "<p>Deletes a document</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "id",
            "description": "<p>The id of the document to remove.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "departmentToken",
            "description": "<p>department token of the department the document belongs to.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "optional": false,
            "field": "OK",
            "description": ""
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "HTTP/1.1 200 OK",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./DocumentService.java",
    "groupTitle": "_documents"
  },
  {
    "type": "get",
    "url": "/id/{id}?apiKey={apiKey}",
    "title": "getDocumentById",
    "name": "getDocumentById",
    "group": "_documents",
    "description": "<p>returns the document with the given id</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "DocumentInfo",
            "optional": false,
            "field": "document",
            "description": "<p>JSON DocumentInfo-Object.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "HTTP/1.1 200 OK",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./DocumentService.java",
    "groupTitle": "_documents"
  },
  {
    "type": "get",
    "url": "/id/{id}/content?apiKey={apiKey}",
    "title": "getDocumentContent",
    "name": "getDocumentContent",
    "group": "_documents",
    "description": "<p>returns the content of the document with the given id</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "byte[]",
            "optional": false,
            "field": "Document",
            "description": "<p>content</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "HTTP/1.1 200 OK",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./DocumentService.java",
    "groupTitle": "_documents"
  },
  {
    "type": "get",
    "url": "/documents/{departmentToken}/groups?apiKey={apiKey}",
    "title": "getDocumentGroups",
    "name": "getDocumentGroups",
    "group": "_documents",
    "description": "<p>gets all document groups (metadata only) within the given department</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "departmentToken",
            "description": "<p>The department token of the department.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "Document",
            "optional": false,
            "field": "documents",
            "description": "<p>JSON DocumentInfo-Objects with information about found documents.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "HTTP/1.1 200 OK",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./DocumentService.java",
    "groupTitle": "_documents"
  },
  {
    "type": "get",
    "url": "/documents/{departmentToken}/{group}/?apiKey={apiKey}",
    "title": "getDocuments",
    "name": "getDocuments",
    "group": "_documents",
    "description": "<p>gets all documents (metadata only) within the given department and document group.</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "departmentToken",
            "description": "<p>The department token of the department.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "group",
            "description": "<p>The document group name within this department to look for documents.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "Document",
            "optional": false,
            "field": "documents",
            "description": "<p>JSON DocumentInfo-Objects with information about found documents.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "HTTP/1.1 200 OK",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./DocumentService.java",
    "groupTitle": "_documents"
  },
  {
    "type": "get",
    "url": "/search/{departmentToken}/documentGroup?apiKey={apiKey}&phrase={phrase}",
    "title": "searchDocuments",
    "name": "searchDocuments",
    "group": "_documents",
    "description": "<p>Returns all the documents in the given department and document group matching the search phrase.</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "departmentToken",
            "description": "<p>The department token of the department.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "phrase",
            "description": "<p>The searchstring to searh for.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "DocumentInfo",
            "optional": false,
            "field": "documents",
            "description": "<p>JSON DocumentInfo-Objects with information about found documents.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "HTTP/1.1 200 OK",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./DocumentService.java",
    "groupTitle": "_documents"
  },
  {
    "type": "put",
    "url": "/id/{id}?apiKey={apiKey}&name={name}&mimeType={mimeType}&tag={tag}&changes={changes}",
    "title": "setDocumentAttributes",
    "name": "setDocumentAttributes",
    "group": "_documents",
    "description": "<p>Changes different attributes of a document</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "name",
            "description": "<p>The new name of the document.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "mimeType",
            "description": "<p>The new MIME-Type of the document.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "tag",
            "description": "<p>The new document tag.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "changes",
            "description": "<p>description of changes made.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "optional": false,
            "field": "OK",
            "description": ""
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "HTTP/1.1 200 OK",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./DocumentService.java",
    "groupTitle": "_documents"
  },
  {
    "type": "get",
    "url": "/list/received",
    "title": "getInboxMessages",
    "name": "getInboxMessages",
    "group": "_inbox",
    "description": "<p>Returns all the messages received for the current user or the user specified by param from.</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "from",
            "description": "<p>The user which inbox to read - if specified</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "List",
            "optional": false,
            "field": "messages",
            "description": "<p>of the user.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "    HTTP/1.1 200 OK\n[\n{\n\"id\": 48,\n\"dateReceived\": 1588448673369,\n\"dateRead\": 1588448711115,\n\"messageRead\": true,\n\"subject\": \"test11\",\n\"from\": {\n\"id\": 44,\n\"name\": \"admin\",\n\"username\": \"admin\",\n\"address\": null\n},\n\"to\": {\n\"id\": 44,\n\"name\": \"admin\",\n\"username\": \"admin\",\n\"address\": null\n}\n}\n]",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./InboxService.java",
    "groupTitle": "_inbox"
  },
  {
    "type": "get",
    "url": "/id/{id}",
    "title": "getMessageById",
    "name": "getMessageById",
    "group": "_inbox",
    "description": "<p>Returns the message with the given id.</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "int",
            "optional": false,
            "field": "id",
            "description": "<p>The id of the message content to read.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "String",
            "optional": false,
            "field": "message",
            "description": "<p>content</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "   HTTP/1.1 200 OK\nTest message OK.",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./InboxService.java",
    "groupTitle": "_inbox"
  },
  {
    "type": "get",
    "url": "/list/sent",
    "title": "getSentMessages",
    "name": "getSentMessages",
    "group": "_inbox",
    "description": "<p>Returns all the messages sent by the current user or the user specified by param from.</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "from",
            "description": "<p>The user which outbox to read - if specified</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "List",
            "optional": false,
            "field": "messages",
            "description": "<p>sent by the user.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "    HTTP/1.1 200 OK\n[\n{\n\"id\": 48,\n\"dateReceived\": 1588448673369,\n\"dateRead\": 1588448711115,\n\"messageRead\": true,\n\"subject\": \"test11\",\n\"from\": {\n\"id\": 44,\n\"name\": \"admin\",\n\"username\": \"admin\",\n\"address\": null\n},\n\"to\": {\n\"id\": 44,\n\"name\": \"admin\",\n\"username\": \"admin\",\n\"address\": null\n}\n}\n]",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./InboxService.java",
    "groupTitle": "_inbox"
  },
  {
    "type": "post",
    "url": "/new",
    "title": "newMessage",
    "name": "newMessage",
    "group": "_inbox",
    "description": "<p>sends a new message to an inbox.</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "username",
            "description": "<p>the recipiants username.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "subject",
            "description": "<p>the subject of the message.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "message",
            "description": "<p>the message to send.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "String",
            "optional": false,
            "field": "success",
            "description": "<p>message.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "    HTTP/1.1 200 OK\n{\n\"response\": \"Message has succcessfully been sent to User admin.\"\n}",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./InboxService.java",
    "groupTitle": "_inbox"
  },
  {
    "type": "delete",
    "url": "/remove",
    "title": "removeMessage",
    "name": "removeMessage",
    "group": "_inbox",
    "description": "<p>deletes the message with the given id.</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "Integer",
            "optional": false,
            "field": "id",
            "description": "<p>the id of the message to be deleted.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "String",
            "optional": false,
            "field": "success",
            "description": "<p>message.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "    HTTP/1.1 200 OK\n{\n\"response\": \"Message has succcessfully been removed.\"\n}",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./InboxService.java",
    "groupTitle": "_inbox"
  },
  {
    "type": "post",
    "url": "/tasks/{taskId}/edit",
    "title": "assignTask",
    "name": "assignTask",
    "group": "_projects",
    "description": "<p>Assignes a task, sets it's status and percentage complete</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "status",
            "description": "<p>The state of the task (ASSIGNED / CREATED / FINISHED...)</p>"
          },
          {
            "group": "Parameter",
            "type": "Integer",
            "optional": false,
            "field": "assignee",
            "description": "<p>The id of the user the task should be assigned to.</p>"
          },
          {
            "group": "Parameter",
            "type": "Integer",
            "optional": false,
            "field": "percentage",
            "description": "<p>The percentage complete value for the task.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "String",
            "optional": false,
            "field": "JSON",
            "description": "<p>Array with the task</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "    HTTP/1.1 200 OK\n[\n{\n\"id\": 49,\n\"priority\": 1,\n\"name\": \"aaa\",\n\"description\": \"aaa\",\n\"taskStatus\": \"CREATED\",\n\"assignee\": null,\n\"timeSpent\": []\n}\n]",
          "type": "json"
        }
      ]
    },
    "version": "0.0.0",
    "filename": "./ProjectService.java",
    "groupTitle": "_projects"
  },
  {
    "type": "get",
    "url": "/{projectId}/tasks",
    "title": "getProjectTasks",
    "name": "getProjectTasks",
    "group": "_projects",
    "description": "<p>Returns all tasks for the project with the given id</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "projectId",
            "description": "<p>ID of the project to retrieve the tasks from.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "String",
            "optional": false,
            "field": "JSON",
            "description": "<p>Array projects found</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "    HTTP/1.1 200 OK\n[\n{\n\"id\": 49,\n\"priority\": 1,\n\"name\": \"aaa\",\n\"description\": \"aaa\",\n\"taskStatus\": \"CREATED\",\n\"assignee\": null,\n\"timeSpent\": []\n}\n]",
          "type": "json"
        }
      ]
    },
    "version": "0.0.0",
    "filename": "./ProjectService.java",
    "groupTitle": "_projects"
  },
  {
    "type": "get",
    "url": "/projects",
    "title": "getProjects",
    "name": "getProjects",
    "group": "_projects",
    "description": "<p>Returns all projects the current user is project lead</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "String",
            "optional": false,
            "field": "JSON",
            "description": "<p>Array projects found</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "    HTTP/1.1 200 OK\n[\n{\n\"id\": 51,\n\"manager\": {\n\"id\": 45,\n\"name\": \"admin\",\n\"username\": \"admin\",\n\"address\": null\n},\n\"name\": \"aaa\",\n\"description\": \"aaa\",\n\"beginDate\": 1588525293108,\n\"dueDate\": 1590357600000,\n\"maxManDays\": 0,\n\"priority\": 0,\n\"users\": [\n\t{\n\"id\": 45,\n\"name\": \"admin\",\n\"username\": \"admin\",\n\"address\": null\n\t}\n],\n\"progress\": {\n\"id\": 57,\n\"targetGoals\": [\n\t{\n\"id\": 55,\n\"task\": {\n\"id\": 49,\n\"priority\": 1,\n\"name\": \"aaa\",\n\"description\": \"aaa\",\n\"taskStatus\": \"CREATED\",\n\"assignee\": null,\n\"timeSpent\": []\n},\n\"projectGoal\": {\n\"id\": 52,\n\"name\": \"aaa\",\n\"description\": \"aaa\",\n\"category\": null,\n\"properties\": [\n{\n\"id\": 53,\n\"name\": \"Task completeness)\",\n\"description\": \"Indicates the percentage value of completeness of a task.\",\n\"min\": 0.0,\n\"max\": 100.0,\n\"tempValue\": 0.0\n\t}\n\t]\n},\n\"propertyRecord\": {\n\"id\": 56,\n\"property\": {\n\"id\": 53,\n\"name\": \"Task completeness)\",\n\"description\": \"Indicates the percentage value of completeness of a task.\",\n\"min\": 0.0,\n\"max\": 100.0,\n\"tempValue\": 0.0\n},\n\"value\": 0.0,\n\"documentInfo\": null,\n\"documentPropertyRecord\": false,\n\"numericPropertyRecord\": true\n},\n\"percentage\": 0\n}\n],\n\"progress\": 0\n\t}\n}\n]",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./ProjectService.java",
    "groupTitle": "_projects"
  },
  {
    "type": "post",
    "url": "/create/{departmentToken}/{group}?apiKey={apiKey}",
    "title": "createResource",
    "name": "createResource",
    "group": "_resources",
    "description": "<p>creates a new resource</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "departmentToken",
            "description": "<p>The department token of the department.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "group",
            "description": "<p>The resource group to put new resource in.</p>"
          },
          {
            "group": "Parameter",
            "optional": false,
            "field": "uuid",
            "description": "<ul> <li>uuid of new resource</li> </ul>"
          },
          {
            "group": "Parameter",
            "optional": false,
            "field": "name",
            "description": "<ul> <li>name of new resource</li> </ul>"
          },
          {
            "group": "Parameter",
            "optional": false,
            "field": "description",
            "description": "<ul> <li>description</li> </ul>"
          },
          {
            "group": "Parameter",
            "optional": false,
            "field": "slots",
            "description": "<ul> <li>max.number of slots for new device</li> </ul>"
          },
          {
            "group": "Parameter",
            "optional": false,
            "field": "ip",
            "description": "<ul> <li>ip address of new device/resource (if applicable)</li> </ul>"
          },
          {
            "group": "Parameter",
            "optional": false,
            "field": "commands",
            "description": "<ul> <li>list of commands the device understands</li> </ul>"
          },
          {
            "group": "Parameter",
            "optional": false,
            "field": "isAgent",
            "description": "<ul> <li>is device an agent?</li> </ul>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "optional": false,
            "field": "200",
            "description": "<p>OK.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "HTTP/1.1 200 OK",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./ResourceService.java",
    "groupTitle": "_resources"
  },
  {
    "type": "delete",
    "url": "/remove/id/{id}?apiKey={apiKey}&departmentToken={departmenttoken}",
    "title": "deleteResourceById",
    "name": "deleteResourceById",
    "group": "_resources",
    "description": "<p>Deletes a resource by id</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "id",
            "description": "<p>The id of the resource to remove.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "departmentToken",
            "description": "<p>department token of the department the resource belongs to.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "optional": false,
            "field": "OK",
            "description": ""
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "HTTP/1.1 200 OK",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./ResourceService.java",
    "groupTitle": "_resources"
  },
  {
    "type": "delete",
    "url": "/remove/uuid/{uuid}?apiKey={apiKey}&departmentToken={departmenttoken}",
    "title": "deleteResourceByUuid",
    "name": "deleteResourceByUuid",
    "group": "_resources",
    "description": "<p>Deletes a resource by uuid</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "uuid",
            "description": "<p>The uuid of the resource to remove.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "departmentToken",
            "description": "<p>department token of the department the resource belongs to.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "optional": false,
            "field": "OK",
            "description": ""
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "HTTP/1.1 200 OK",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./ResourceService.java",
    "groupTitle": "_resources"
  },
  {
    "type": "get",
    "url": "/id/{id}?apiKey={apiKey}",
    "title": "getResourceById",
    "name": "getResourceById",
    "group": "_resources",
    "description": "<p>returns the resource/device with the given id</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "Resource",
            "optional": false,
            "field": "resource/device",
            "description": "<p>Object.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "HTTP/1.1 200 OK",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./ResourceService.java",
    "groupTitle": "_resources"
  },
  {
    "type": "get",
    "url": "/list/{departmentToken}/groups?apiKey={apiKey}",
    "title": "getResourceGroups",
    "name": "getResourceGroups",
    "group": "_resources",
    "description": "<p>gets all resource groups within the given department.</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "departmentToken",
            "description": "<p>The department token of the department.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "optional": false,
            "field": "List",
            "description": "<p>of {ResourceGroup} objects with information about found resource groups.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "HTTP/1.1 200 OK",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./ResourceService.java",
    "groupTitle": "_resources"
  },
  {
    "type": "get",
    "url": "/list/{departmentToken}/{group}?apiKey={apiKey}",
    "title": "getResources",
    "name": "getResources",
    "group": "_resources",
    "description": "<p>gets all resources within the given department and group.</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "departmentToken",
            "description": "<p>The department token of the department.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "group",
            "description": "<p>The resource group of the resources.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "optional": false,
            "field": "List",
            "description": "<p>of {Resource} objects.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "HTTP/1.1 200 OK",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./ResourceService.java",
    "groupTitle": "_resources"
  },
  {
    "type": "put",
    "url": "/id/{id}?apiKey={apiKey}&name={name}&description={description}&mqttOnline={mqttOnline}&commands={commands}&geo=[geo}&set={set}",
    "title": "updateResource",
    "name": "updateResource",
    "group": "_resources",
    "description": "<p>Changes different attributes of a resource</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "name",
            "description": "<p>The new name of the resource. omit if no changes.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "description",
            "description": "<p>The new description of the resource. omit if no changes.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "mqttOnline",
            "description": "<p>(true/false) - Set the resources online state. omit if no changes.</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "commands",
            "description": "<p>update command set the resource understands. separate by colon (e.G ON:OFF:RESET)</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "geo",
            "description": "<p>new coordinates of the resource (LAT:LONG)- leave blank if no changes</p>"
          },
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "set",
            "description": "<p>set a specific resource attribute to a specific value (e.G. NAME:WERT)</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "optional": false,
            "field": "OK",
            "description": ""
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "HTTP/1.1 200 OK",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./ResourceService.java",
    "groupTitle": "_resources"
  },
  {
    "type": "get",
    "url": "/users/{id}",
    "title": "getUserById",
    "name": "getUserById",
    "group": "_users",
    "description": "<p>Returns user with the given {id}</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "User",
            "optional": false,
            "field": "JSON-Object",
            "description": "<p>with the user with id {id}.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "   HTTP/1.1 200 OK\n{\n \"id\": 48,\n \"name\": \"peter\",\n \"username\": \"peter\"\n}",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./UserRoleService.java",
    "groupTitle": "_users"
  },
  {
    "type": "get",
    "url": "/users/department/{departmentToken}",
    "title": "getUsers",
    "name": "getUsers",
    "group": "_users",
    "description": "<p>Returns all users within the department with token {departmentToken}</p>",
    "parameter": {
      "fields": {
        "Parameter": [
          {
            "group": "Parameter",
            "type": "String",
            "optional": false,
            "field": "apiKey",
            "description": "<p>The API-Key of the user accessing the service.</p>"
          }
        ]
      }
    },
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "type": "List",
            "optional": false,
            "field": "JSON-Array",
            "description": "<p>with all users in this department.</p>"
          }
        ]
      },
      "examples": [
        {
          "title": "Success-Response:",
          "content": "    HTTP/1.1 200 OK\n[\n {\n  \"id\": 48,\n  \"name\": \"peter\",\n  \"username\": \"peter\"\n },\n {\n  \"id\": 54,\n  \"name\": \"torsten\",\n  \"username\": \"torsten\"\n }\n]",
          "type": "json"
        }
      ]
    },
    "error": {
      "fields": {
        "Error 4xx": [
          {
            "group": "Error 4xx",
            "optional": false,
            "field": "NotAuthorized",
            "description": "<p>APIKey incorrect.</p>"
          }
        ]
      }
    },
    "version": "0.0.0",
    "filename": "./UserRoleService.java",
    "groupTitle": "_users"
  },
  {
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "optional": false,
            "field": "varname1",
            "description": "<p>No type.</p>"
          },
          {
            "group": "Success 200",
            "type": "String",
            "optional": false,
            "field": "varname2",
            "description": "<p>With type.</p>"
          }
        ]
      }
    },
    "type": "",
    "url": "",
    "version": "0.0.0",
    "filename": "./apidoc/main.js",
    "group": "c__Entwicklung_projekteIntelliJ_PrioritizeEJB_ejbModule_de_hallerweb_enterprise_prioritize_view_boundary_apidoc_main_js",
    "groupTitle": "c__Entwicklung_projekteIntelliJ_PrioritizeEJB_ejbModule_de_hallerweb_enterprise_prioritize_view_boundary_apidoc_main_js",
    "name": ""
  },
  {
    "success": {
      "fields": {
        "Success 200": [
          {
            "group": "Success 200",
            "optional": false,
            "field": "varname1",
            "description": "<p>No type.</p>"
          },
          {
            "group": "Success 200",
            "type": "String",
            "optional": false,
            "field": "varname2",
            "description": "<p>With type.</p>"
          }
        ]
      }
    },
    "type": "",
    "url": "",
    "version": "0.0.0",
    "filename": "./doc/main.js",
    "group": "c__Entwicklung_projekteIntelliJ_PrioritizeEJB_ejbModule_de_hallerweb_enterprise_prioritize_view_boundary_doc_main_js",
    "groupTitle": "c__Entwicklung_projekteIntelliJ_PrioritizeEJB_ejbModule_de_hallerweb_enterprise_prioritize_view_boundary_doc_main_js",
    "name": ""
  }
] });
