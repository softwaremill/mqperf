#!/usr/bin/osascript
on run argv
if application "iTerm" is running then
	tell application "iTerm"
		tell application "System Events" to keystroke "t" using command down
		tell current tab of current window
			select
			tell current session
				
				split horizontally with default profile
				
				set num_hosts to count of argv
				repeat with n from 1 to num_hosts
					if n - 1 is (round (num_hosts / 2)) then
						-- move to lower split
						tell application "System Events" to keystroke "]" using command down
					else if n > 1 then
						-- split vertically
						tell application "System Events" to keystroke "d" using command down
					end if
					delay 1
					write text "cd ~/projects/mqperf"
					write text "source scripts/conf_env"
					write text "ssh -i ansible/mqperf-key.pem ec2-user@" & (item n of argv)
				end repeat
			end tell
		end tell
	end tell
else
	activate application "iTerm"	
end if
end run
