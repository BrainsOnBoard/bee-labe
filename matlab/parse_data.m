function datafile = parse_data(path)
%PARSE_DATA Loads JSON-formatted data from the Bee-labe app.
%   Path can be a file, directory or pattern matching several files
%   (e.g. 'data/*_AP.json').
%
%   The function returns a struct array representing the data files,
%   including metadata and vectors of time (in ms) and yaw (compass bearing),
%   pitch and roll (all in radians).
%
%   This function needs either a newer version of Matlab (R2016b+) or the
%   jsonlab toolbox (from Matlab exchange).

% if param is a folder, load all JSON files in that folder
if isdir(path)
    dname = path;
    path = fullfile(path,'*.json');
else
    % otherwise get the path to the file
    dname = fileparts(path);
end

d = dir(path);
for i = 1:length(d)
    filename = fullfile(dname,d(i).name);
    
    % use appropriate function to load JSON, then turn data into cleaner
    % format
    if exist('jsondecode','builtin')
        fid = fopen(filename);
        data = jsondecode(char(fread(fid)'));
        fclose(fid);
        
        % (jsondecode gives the data as a struct)
        data.time = cell2mat({data.data.time})';
        data.yaw = cell2mat({data.data.yaw})';
        data.pitch = cell2mat({data.data.pitch})';
        data.roll = cell2mat({data.data.roll})';
    elseif exist('loadjson','file')
        data = loadjson(filename);
        
        % (loadjson gives the data as a cell array of structs)
        data.time = cellfun(@(x)x.time,data.data)';
        data.yaw = cellfun(@(x)x.yaw,data.data)';
        data.pitch = cellfun(@(x)x.pitch,data.data)';
        data.roll = cellfun(@(x)x.roll,data.data)';
    else
        error('This function needs either a newer version of Matlab (R2016b+) or the jsonlab toolbox (from Matlab exchange).');
    end
    
    % get the new data struct minus the data field
    datafile(i) = rmfield(data,'data');
end