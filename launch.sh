#Get git repo
git clone https://github.com/MaartenVanLoo/UA_1515FTIDSS_Distributed-Systems-Public_Project.git Distributed

#try to remote start a node
mvn exec:java -Dexec.mainClass=startupNode.startup

#create new launch file
touch launch.sh
chmod +x launch.sh
nano launch.sh

#Node launch //NOTE: change name!

name="Node3"
branch="testAgentBranch" #default
while getopts ":n:b:" flag
do
        case "${flag}" in
                n) name="${OPTARG}";;
				b) branch="${OPTARG}";;
	   esac
done

echo "name: ${name}"
echo "branch = ${branch}"

#cd ~/UA_1515FTIDSS_Distributed-Systems-Public_Project
cd Distributed
rm -frd local
rm -frd replica
rm -frd log
git clean -f
git fetch --all
git reset --hard origin/${branch}
mvn package
#cd ~/UA_1515FTIDSS_Distributed-Systems-Public_Project
cd Distributed

cp -r local_${name} local

mvn exec:java -Dexec.mainClass=Node.Node -Dexec.args=${name}.6dist




#nameserver

name="Nameserver"
branch="testAgentBranch" #default
while getopts ":n:b:" flag
do
        case "${flag}" in
                n) name="${OPTARG}";;
				b) branch="${OPTARG}";;
	   esac
done

echo "name: ${name}"
echo "branch = ${branch}"

cd Distributed
#cd ~/UA_1515FTIDSS_Distributed-Systems-Public_Project
rm -frd local
rm -frd replica
rm -frd log
rm -f nameServerMap.json
git clean -f
git fetch --all
git reset --hard origin/${branch}
mvn package
cd Distributed
#cd ~/UA_1515FTIDSS_Distributed-Systems-Public_Project

mvn spring-boot:run



