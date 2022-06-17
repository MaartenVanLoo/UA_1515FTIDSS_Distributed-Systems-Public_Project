
## INIT

    cd ~
    apt install git
    apt install maven -y
    git clone https://github.com/MaartenVanLoo/UA_1515FTIDSS_Distributed-Systems-Public_Project
    cd UA_1515FTIDSS_Distributed-Systems-Public_Project
    git clean -f
    git pull
    mvn package
 
## Run Nameserver

    mvn spring-boot:run


## Run Node

    # Does not work when certain dependencies are added 
    #java -cp target/G1-ProjectY-1.0.jar Node.Node Node0
    #java -cp target/G1-ProjectY-1.0.jar Node.Node Node1
    #java -cp target/G1-ProjectY-1.0.jar Node.Node Node2
    #java -cp target/G1-ProjectY-1.0.jar Node.Node Node3
    #java -cp target/G1-ProjectY-1.0.jar Node.Node Node4

    # In stead we need to use these commands:
    mvn exec:java -Dexec.mainClass="Node.Node" -Dexec.args="Node0"
    mvn exec:java -Dexec.mainClass="Node.Node" -Dexec.args="Node1"
    mvn exec:java -Dexec.mainClass="Node.Node" -Dexec.args="Node2"
    mvn exec:java -Dexec.mainClass="Node.Node" -Dexec.args="Node3"
    mvn exec:java -Dexec.mainClass="Node.Node" -Dexec.args="Node4"


## Git Pull

    #cd ~/UA_1515FTIDSS_Distributed-Systems-Public_Project
    #git clean -f
    #git reset --hard
    #git pull
    #mvn package

    cd ~/UA_1515FTIDSS_Distributed-Systems-Public_Project
    git clean -f
    git fetch --all
    git reset --hard origin/Discovery
    clear


## Clean

    cd ~
    rm -rd UA_1515FTIDSS_Distributed-Systems-Public_Project
    git clone https://github.com/MaartenVanLoo/UA_1515FTIDSS_Distributed-Systems-Public_Project
    cd UA_1515FTIDSS_Distributed-Systems-Public_Project

## Useful links

- https://spring.io/blog/2013/11/01/exception-handling-in-spring-mvc
- https://www.restapitutorial.com/lessons/httpmethods.html#:~:text=The%20primary%20or%20most%2Dcommonly,but%20are%20utilized%20less%20frequently.
- https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/GET
- https://stackoverflow.com/questions/39835648/how-do-i-get-the-json-in-a-response-body-with-spring-annotaion
