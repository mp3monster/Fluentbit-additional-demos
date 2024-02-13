curl  -d "channel=CMM13QSBB" -d "limit=3" -d "include_all_metadata=false" -H "Authorization: Bearer xoxb-735037803329-6179690984166-MLSoE7JpBbyIQ2i1MDkFmqQY" -X POST https://slack.com/api/conversations.history
REM https://api.slack.com/methods/conversations.history
REM add oldest - with the timestamp seconds.nanoseconds -- then we can limit 
REM 'curl  -d "channel=CHANNELID" -d "limit=3" -H "Authorization: Bearer xoxb-YOUR TOKEN HERE" -X POST https://slack.com/api/conversations.history
REM #https://slack.com/api/conversations.history?channel=CMM13QSBB&limit=3&pretty=1

REM #https://api.slack.com/methods/conversations.replies
