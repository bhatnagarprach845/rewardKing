# cashbackking

+---------------------------------------------------------------------------------+
|                                   CLIENT LAYER                                  |
|  [ React SPA / AWS Amplify ] ──> (Exchanges user tokens with Amazon Cognito)    |
+---------------------------------------------------------------------------------+
│
│ HTTPS Rest Requests + Bearer JWT
v
+---------------------------------------------------------------------------------+
|                            ROUTING & COMPUTE LAYER                              |
|  [ AWS Lambda Function URL ]                                                    |
|        │                                                                        |
|        └──> [ StreamLambdaHandler.java ] (Intercepts JSON stream payload)       |
|                   │                                                             |
|                   └──> [ aws-serverless-java-container ] (Simulates Tomcat)     |
|                             │                                                   |
|                             └──> [ Spring Boot DispatcherServlet ]              |
|                                       │                                         |
|                                       └──> [ Spring Security Filter Chain ]     |
+---------------------------------------------------------------------------------+
│
┌──────────────────────────────┼──────────────────────────────┐
v                              v                              v
+------------------------------+ +------------------------------+ +------------------------------+
|     EXTERNAL SERVICES        | |       BUSINESS SERVICES      | |         DATA LAYER          |
|  [ AWS Textract ]            | |  [ ReceiptProcessor.java ]   | |  [ Supabase PostgreSQL ]    |
|  (Scans image bytes for lines| |  (Checks EXIF, runs math,    | |  (Maintains relational state|
|   merchant data, totals)     | |   hashes fraud, awards balances)  for transactions, wallets)|
+------------------------------+ +------------------------------+ +------------------------------+

