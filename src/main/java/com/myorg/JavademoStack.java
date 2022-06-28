package com.myorg;

import java.util.List;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.ec2.BastionHostLinux;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.rds.AuroraMysqlClusterEngineProps;
import software.amazon.awscdk.services.rds.AuroraMysqlEngineVersion;
import software.amazon.awscdk.services.rds.DatabaseCluster;
import software.amazon.awscdk.services.rds.DatabaseClusterEngine;
import software.amazon.awscdk.services.rds.InstanceProps;
import software.constructs.Construct;

public class JavademoStack extends Stack {

  public JavademoStack(final Construct parent, final String id) {
    this(parent, id, null);
  }

  public JavademoStack(final Construct parent, final String id, final StackProps props) {
    super(parent, id, props);

    // create  subnet configuration start
    //public subnet
    SubnetConfiguration publicSubnet =
        SubnetConfiguration.builder()
            .subnetType(SubnetType.PUBLIC)
            .cidrMask(24)
            .name("public-subnet")
            .build();

    //web private subnet
    SubnetConfiguration webPrivateSubnet =
        SubnetConfiguration.builder()
            .subnetType(SubnetType.PRIVATE_WITH_NAT)
            .cidrMask(24)
            .name("web-private-subnet")
            .build();

    //app private subnet
    SubnetConfiguration appPrivateSubnet =
        SubnetConfiguration.builder()
            .subnetType(SubnetType.PRIVATE_WITH_NAT)
            .cidrMask(24)
            .name("app-private-subnet")
            .build();

    //db private subnet
    SubnetConfiguration dbPrivateSubnet =
        SubnetConfiguration.builder()
            .subnetType(SubnetType.PRIVATE_ISOLATED)
            .cidrMask(24)
            .name("db-private-subnet")
            .build();

    List<SubnetConfiguration> subnetConfigurationList =
        List.of(publicSubnet, webPrivateSubnet, appPrivateSubnet, dbPrivateSubnet);
    // create  subnet configuration end

    // create vpc subnet natgateway internetgateway security group
    Vpc vpc = Vpc.Builder.create(this, "myVpc")
        .natGateways(1)
        .cidr("10.1.0.0/16")
        .maxAzs(3)
        .vpcName("myVpc")
        .subnetConfiguration(subnetConfigurationList)
        .build();

    // create application targetGroup
    ApplicationTargetGroup targetGroup = ApplicationTargetGroup.Builder.create(this, "TG")
        .targetGroupName("web-tg")
        .vpc(vpc)
        .port(80)
        .build();

    // create Certificate
//    Certificate certificate = Certificate.Builder.create(this, "Cert")
//        .domainName("xd.dev")
//        .subjectAlternativeNames(List.of("*.xd.dev"))
//        .validation(CertificateValidation.fromDns())
//        .build();
//    IListenerCertificate iListenerCertificate = ListenerCertificate.fromCertificateManager(certificate);

    // create application loadbalance
    ApplicationListener listener = ApplicationListener.Builder.create(this, "Listener")
        .loadBalancer(ApplicationLoadBalancer.Builder.create(this, "ALB")
            .loadBalancerName("web-alb")
            .vpc(vpc)
            .internetFacing(true)
            .build())
//        .certificates(List.of(iListenerCertificate))
//        .protocol(ApplicationProtocol.HTTPS)
        .protocol(ApplicationProtocol.HTTP)
        .defaultTargetGroups(List.of(targetGroup))
        .build();

    //========================================= create ecs cluster start ========================

    // create ecs cluster
    Cluster cluster = Cluster.Builder.create(this, "web-ecs-cluster")
        .clusterName("web-ecs-cluster")
        .vpc(vpc)
        .build();

    // fargateTaskDefinition
    FargateTaskDefinition taskDefinition =
        FargateTaskDefinition.Builder.create(this, "FargateTaskDef")
            .cpu(256)
            .memoryLimitMiB(512)
            .build();

    // add Container config
    taskDefinition.addContainer("PHP", ContainerDefinitionOptions.builder()
            .image(ContainerImage.fromRegistry("amazon/amazon-ecs-sample"))
            .build())
        .addPortMappings(PortMapping.builder().containerPort(80).build());

    // crate fargateService
    FargateService fargateService = FargateService.Builder.create(this, "fargate-service")
        .serviceName("fargate-service")
        .cluster(cluster)
        .taskDefinition(taskDefinition)
        .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PRIVATE_WITH_NAT).build())
        .build();

    // ecs cluster autoScale config
    fargateService.autoScaleTaskCount(EnableScalingProps.builder()
        .maxCapacity(20)
        .minCapacity(4)
        .build());

    // config targetGroup with fargateService
    targetGroup.addTarget(fargateService);

    //=========================================  create ecs cluster  end ========================

    //bastion SecurityGroup
    SecurityGroup bastionSg = SecurityGroup.Builder.create(this, "bastion-sg")
        .vpc(vpc)
        .allowAllOutbound(true)
        .securityGroupName("bastion-sg")
        .description("Allow ssh access to ec2 instances")
        .build();
    // add ingress rule
    bastionSg.addIngressRule(Peer.anyIpv4(), Port.tcp(22));

    //Bastion
    BastionHostLinux bastionHostLinux =
        BastionHostLinux.Builder.create(this, "bastion")
            .vpc(vpc)
            .instanceName("bastion")
            .instanceType(new InstanceType("t1.micro"))
            .subnetSelection(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
            .securityGroup(bastionSg)
            .build();

    // rds security group
    SecurityGroup rdsSg = SecurityGroup.Builder.create(this, "rds-sg")
        .vpc(vpc)
        .allowAllOutbound(true)
        .securityGroupName("rds-sg")
        .description("Allow ecs access to rds")
        .build();
    rdsSg.addIngressRule(Peer.anyIpv4(), Port.tcp(3306));

    // create  mysql cluster
    DatabaseCluster dbCluster = DatabaseCluster.Builder.create(this, "mysql-cluster")
        .defaultDatabaseName("mysqldb")
        .engine(DatabaseClusterEngine.auroraMysql(
            AuroraMysqlClusterEngineProps.builder().version(AuroraMysqlEngineVersion.VER_2_08_1).build()))
        .instanceProps(InstanceProps.builder()
            // optional , defaults to t3.medium
            .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.SMALL))
            .vpcSubnets(SubnetSelection.builder()
                .subnetType(SubnetType.PRIVATE_ISOLATED)
                .build())
            .vpc(vpc)
            .securityGroups(List.of(rdsSg))
            .build())
        .build();

    //allow access from fargateService connection
    dbCluster.getConnections().allowFrom(fargateService, Port.tcp(3306));

  }
}
