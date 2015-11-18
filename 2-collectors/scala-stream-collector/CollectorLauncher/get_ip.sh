ip="$(ifconfig | grep -A 1 'eth0' | tail -1 | cut -d ':' -f 2 | cut -d ' ' -f 1)"
echo ' LOCAL IP ADRESS '
echo $ip
echo 'Editing configuration file'
sed  -i "s/\(interface *= *\).*/\1\"$ip\"/" collector5.conf
