# AWS CLI Commands Cheat Sheet

## Basics

- `aws configure`: Configure AWS CLI with access key, secret key, region, and output format.

## S3 (Simple Storage Service)

### Buckets

- `aws s3 mb s3://bucket-name`: Create an S3 bucket.
- `aws s3 rb s3://bucket-name`: Remove an empty S3 bucket.
- `aws s3 ls`: List all S3 buckets.

### Objects

- `aws s3 cp file.txt s3://bucket-name/`: Upload a file to S3 bucket.
- `aws s3 sync . s3://bucket-name/`: Sync local directory with S3 bucket.
- `aws s3 ls s3://bucket-name/`: List objects in an S3 bucket.
- `aws s3 mv s3://bucket-name/file.txt s3://bucket-name/newfile.txt`: Rename an object in S3 bucket.
- `aws s3 rm s3://bucket-name/file.txt`: Delete an object from S3 bucket.

### Permissions

- `aws s3 presign s3://bucket-name/file.txt`: Generate a presigned URL for downloading an S3 object.

## EC2 (Elastic Compute Cloud)

### Instances

- `aws ec2 describe-instances`: List all EC2 instances.
- `aws ec2 start-instances --instance-ids i-1234567890abcdef0`: Start an EC2 instance.
- `aws ec2 stop-instances --instance-ids i-1234567890abcdef0`: Stop an EC2 instance.
- `aws ec2 terminate-instances --instance-ids i-1234567890abcdef0`: Terminate an EC2 instance.

### Security Groups

- `aws ec2 describe-security-groups`: List all security groups.
- `aws ec2 create-security-group --group-name my-sg --description "My Security Group"`: Create a new security group.
- `aws ec2 authorize-security-group-ingress --group-id sg-12345678 --protocol tcp --port 22 --cidr 0.0.0.0/0`: Allow SSH access to a security group.

## IAM (Identity and Access Management)

### Users

- `aws iam create-user --user-name myuser`: Create an IAM user.
- `aws iam delete-user --user-name myuser`: Delete an IAM user.
- `aws iam list-users`: List all IAM users.

### Roles

- `aws iam create-role --role-name myrole --assume-role-policy-document file://trust-policy.json`: Create an IAM role.
- `aws iam delete-role --role-name myrole`: Delete an IAM role.
- `aws iam list-roles`: List all IAM roles.

## Lambda

- `aws lambda list-functions`: List all Lambda functions.
- `aws lambda create-function --function-name myfunction --runtime nodejs14.x --handler index.handler --zip-file fileb://function.zip`: Create a Lambda function.
- `aws lambda delete-function --function-name myfunction`: Delete a Lambda function.

## CloudFormation

- `aws cloudformation create-stack --stack-name mystack --template-body file://template.yaml`: Create a CloudFormation stack.
- `aws cloudformation delete-stack --stack-name mystack`: Delete a CloudFormation stack.
- `aws cloudformation describe-stacks`: Describe all CloudFormation stacks.

## CloudWatch

- `aws cloudwatch list-metrics`: List all CloudWatch metrics.
- `aws cloudwatch put-metric-data --namespace MyNamespace --metric-name MyMetric --value 10 --timestamp 2022-01-01T00:00:00Z`: Put metric data into CloudWatch.

## ECS (Elastic Container Service)

- `aws ecs list-clusters`: List all ECS clusters.
- `aws ecs create-cluster --cluster-name mycluster`: Create an ECS cluster.
- `aws ecs delete-cluster --cluster mycluster`: Delete an ECS cluster.

## SNS (Simple Notification Service)

- `aws sns list-topics`: List all SNS topics.
- `aws sns create-topic --name mytopic`: Create an SNS topic.
- `aws sns delete-topic --topic-arn arn:aws:sns:us-east-1:123456789012:mytopic`: Delete an SNS topic.

## SQS (Simple Queue Service)

- `aws sqs list-queues`: List all SQS queues.
- `aws sqs create-queue --queue-name myqueue`: Create an SQS queue.
- `aws sqs delete-queue --queue-url https://sqs.us-east-1.amazonaws.com/123456789012/myqueue`: Delete an SQS queue.

