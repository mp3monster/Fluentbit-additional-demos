function getOS()
  local osname
  -- ask LuaJIT first
  if jit then
    return jit.os
  end

  -- Unix, Linux variants
  local fh, err = assert(io.popen("uname -o 2>/dev/null", "r"))
  if fh then
    osname = fh:read()
  end

  return osname or "Windows"
end

function printRecord(record)
  for key, value in pairs(record) do
    local elementType = type(value)
    if (elementType == "table") then
      print(string.format("%s { %s = ", indent, key))
      printDetails(value, indent .. " ")
      print("}")
    else
      print(string.format("%s %s = %s --> %s", indent, key, tostring(value), elementType))
    end
  end
end

function cb_osCommand(tag, timestamp, record)
  local code = 0
  local commadAttribute = "cmd"
  local command = "ls"
  --[[printRecord(record)--]]

  if (record[commadAttribute] ~= nil) then
    command = record[commadAttribute]
    if (getOS() == "Windows") then
      command = "call cmd_" .. command .. ".bat"
    else
      command = "source cmd_" .. command .. ".sh"
    end
  else
    print("Lua no command identified")
  end

  local fullCommand = command .. " > remotecmd.lua.out"
  print(fullCommand)
  local runCommandResult = os.execute(fullCommand)
  print("response from exe command:" .. runCommandResult)
  return code, timestamp, record
end
