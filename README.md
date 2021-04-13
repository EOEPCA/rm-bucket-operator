# bucket-operator

## General concept

For background please refer to the description of [Workspace Provisioning](https://github.com/EOEPCA/rm-workspace-api/wiki/Workspace-Provisioning-concepts). The general idea is to 

Step 1: watch for the creation of a [Kubernetes Bucket CRD](https://github.com/EOEPCA/rm-workspace-api/wiki/Workspace-representation-in-Kubernetes) in the Kubernetes cluster reflecting the need for user workspace provisioning

Step 2: fulfill this demand by creating S3 compliant object storage bucket (platform specific!)

Step 3: communicate the successful creation of the bucket to EOEPCA components by exposing necessary access details via a [Kubernetes secret](https://github.com/EOEPCA/rm-workspace-api/wiki/Workspace-representation-in-Kubernetes)

Note: Step 2 can either be 
- **automated** - as done with the implementation for [CreoDias](https://creodias.eu/) / OpenStack with this bucket-operator demonstrator

or

- **performed** manually - by creating a bucket on the platform and manually creating a secret with access details in the Kubernetes cluster afterwards)

## Implementation Details
This project **demonstrates** how the fulfillment process of specific platform resources like user specific object storage could be automated - the current implementation uses the [openstack4j](http://www.openstack4j.com/) java library as well as the [AWS S3](https://aws.amazon.com/sdk-for-java/) java SDK to  

1. create an **OpenStack project** for a user

2. create an **OpenStack user identity** within the OpenStack project for the user

3. create an **OpenStack container** (i.e. S3 compliant object storage bucket) within OpenStack project for the user

4. **link** the OpenStack user identity to the OpenStack project

5. assign a **bucket policy** to grant access to other OpenStack user identities (e.g. the ADES component) to the created OpenStack container 

## Config

To create proper platform resources on OpenStack the following environment variables for the bucket-operator project have to be provided:

- **OS_USERNAME**, **OS_PASSWORD**, **OS_DOMAINNAME** of an EOEPCA operations user with administrative permissions to create new projects, users and containers via OpenStack API

- **OS_MEMBERROLEID** of a specific role grouping users of the EOEPCA operations team to grant them access to the newly created user project (support, troubleshooting,...)

- **OS_SERVICEPROJECTID** of a project containing the OpenStack user identity of EOEPCA components requiring write permissions on the created user bucket (e.g. ADES)

- **USER_EMAIL_PATTERN** associated to the created user within the created user project

This demonstrator only aims to grant user access to the created user bucket, not to other OpenStack resources, i.e. we put an EOEPCA operations email here.

Note: `<name>` is templated and will be replaced, e.g. `eoepca+<name>@eoepca-operator.org`

See [here](https://github.com/EOEPCA/eoepca/blob/develop/system/clusters/develop/resource-management/bucket-operator/deployment.yaml) for example values corresponding to the EOEPCA development cluster.   


