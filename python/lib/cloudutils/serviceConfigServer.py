from db import Database
from configFileOps import configFileOps
from serviceConfig import serviceCfgBase
from cloudException import CloudRuntimeException, CloudInternalException
from utilities import bash
import os

class cloudManagementConfig(serviceCfgBase):
    def __init__(self, syscfg):
        super(cloudManagementConfig, self).__init__(syscfg)
        self.serviceName = "CloudStack Management Server"
    
    def config(self):
        if self.syscfg.env.svrMode == "mycloud":
            cfo = configFileOps("/usr/share/cloud/management/conf/environment.properties", self)
            cfo.addEntry("cloud-stack-components-specification", "components-cloudzones.xml")
            cfo.save()

            cfo = configFileOps("/usr/share/cloud/management/conf/db.properties", self)
            dbHost = cfo.getEntry("db.cloud.host")
            dbPort = cfo.getEntry("db.cloud.port")
            dbUser = cfo.getEntry("db.cloud.username")
            dbPass = cfo.getEntry("db.cloud.password")
            if dbPass.strip() == "":
                dbPass = None
            dbName = cfo.getEntry("db.cloud.name")
            db = Database(dbUser, dbPass, dbHost, dbPort, dbName)
            
            try:
                db.testConnection()
            except CloudRuntimeException, e:
                raise e
            except:
                raise CloudInternalException("Failed to connect to Mysql server")

            try:
                statement = """ UPDATE configuration SET value='%s' WHERE name='%s'"""
                
                db.execute(statement%('true','use.local.storage'))
                db.execute(statement%('20','max.template.iso.size'))
                
                statement = """ UPDATE vm_template SET url='%s',checksum='%s' WHERE id='%s' """
                db.execute(statement%('https://rightscale-cloudstack.s3.amazonaws.com/kvm/RightImage_CentOS_5.4_x64_v5.6.28.qcow2.bz2', '90fcd2fa4d3177e31ff296cecb9933b7', '4'))
                
                statement="""UPDATE disk_offering set use_local_storage=1"""
                db.execute(statement)
            except:
                raise e
            
            #add DNAT 443 to 8250
            if not bash("iptables-save |grep PREROUTING | grep 8250").isSuccess():
                bash("iptables -A PREROUTING -t nat -p tcp --dport 443 -j REDIRECT --to-port 8250 ")
             
            #generate keystore
            keyPath = "/var/lib/cloud/management/web.keystore"
            if not os.path.exists(keyPath):
                cmd = bash("keytool -genkey -keystore %s -storepass \"cloud.com\" -keypass \"cloud.com\" -validity 3650 -dname cn=\"Cloudstack User\",ou=\"mycloud.cloud.com\",o=\"mycloud.cloud.com\",c=\"Unknown\""%keyPath)
               
                if not cmd.isSuccess():
                    raise CloudInternalException(cmd.getErrMsg())
            
                cfo = configFileOps("/etc/cloud/management/tomcat6.conf", self)
                cfo.add_lines("JAVA_OPTS+=\" -Djavax.net.ssl.trustStore=%s \""%keyPath)
        
        try:
            self.syscfg.svo.disableService("tomcat6")
        except:
            pass
            
        self.syscfg.svo.stopService("cloud-management")
        if self.syscfg.svo.enableService("cloud-management"):
            return True
        else:
            raise CloudRuntimeException("Failed to configure %s, please see the /var/log/cloud/setupManagement.log for detail"%self.serviceName) 
            
